(ns glass.service.openai.codex
  (:require
   [clojure.java.io :as io]
   [glass.json :as json])
  (:import
   [java.io BufferedReader BufferedWriter]
   [java.lang ProcessBuilder]))

(def ^:private method-not-found -32601)

(defn- ensure-reader
  [reader]
  (if (instance? BufferedReader reader)
    reader
    (BufferedReader. reader)))

(defn- ensure-writer
  [writer]
  (if (instance? BufferedWriter writer)
    writer
    (BufferedWriter. writer)))

(defn- client
  [{:keys [process reader writer]}]
  {:process process
   :reader (ensure-reader reader)
   :writer (ensure-writer writer)
   :id-counter (atom 0)
   :transport-lock (Object.)})

(defn- with-transport-lock
  [codex f]
  (locking (:transport-lock codex)
    (f)))

(defn- start-process
  []
  (let [^Process process
        (.start (ProcessBuilder.
                 ^"[Ljava.lang.String;"
                 (into-array String ["codex" "app-server" "--listen" "stdio://"])))]
    {:process process
     :reader (io/reader (.getInputStream process))
     :writer (io/writer (.getOutputStream process))}))

(defn close
  [{:keys [process ^BufferedReader reader ^BufferedWriter writer]}]
  (when writer
    (.close writer))
  (when reader
    (.close reader))
  (when process
    (.destroy process))
  nil)

(defn- next-id
  [codex]
  (swap! (:id-counter codex) inc))

(defn- write-message!
  [codex message]
  (let [^BufferedWriter writer (:writer codex)]
    (.write writer (json/stringify message))
    (.write writer "\n")
    (.flush writer)))

(defn- read-message!
  [codex]
  (let [^BufferedReader reader (:reader codex)
        line (.readLine reader)]
    (if line
      (json/parse line)
      (throw (ex-info "[openai/codex] stream closed"
                      {:type :eof})))))

(defn- throw-rpc-error
  [method id {:keys [code message data]}]
  (throw (ex-info (str "[openai/codex] request failed: " method)
                  {:method method
                   :id id
                   :code code
                   :message message
                   :data data})))

(defn- server-request?
  [message]
  (and (contains? message :id)
       (contains? message :method)))

(defn- response?
  [message]
  (and (contains? message :id)
       (or (contains? message :result)
           (contains? message :error))))

(defn- handle-server-request!
  [codex {:keys [id method]}]
  (case method
    "item/commandExecution/requestApproval"
    (write-message! codex {:id id
                           :result {:decision "decline"}})

    "item/fileChange/requestApproval"
    (write-message! codex {:id id
                           :result {:decision "decline"}})

    (write-message! codex {:id id
                           :error {:code method-not-found
                                   :message (str "Unsupported server request: " method)}})))

(defn- await-response!
  [codex id method]
  (loop []
    (let [message (read-message! codex)]
      (cond
        (server-request? message)
        (do
          (handle-server-request! codex message)
          (recur))

        (response? message)
        (if (= id (:id message))
          (if-let [error (:error message)]
            (throw-rpc-error method id error)
            (:result message))
          (throw (ex-info "[openai/codex] unexpected response id"
                          {:expected-id id
                           :actual-id (:id message)})))

        :else
        (recur)))))

(defn- request!
  [codex method params]
  (with-transport-lock
    codex
    (fn []
      (let [id (next-id codex)]
        (write-message! codex
                        (cond-> {:id id
                                 :method method}
                          (some? params)
                          (assoc :params params)))
        (await-response! codex id method)))))

(defn- notify!
  [codex method params]
  (with-transport-lock
    codex
    (fn []
      (write-message! codex
                      (cond-> {:method method}
                        (some? params)
                        (assoc :params params))))))

(defn- initialize!
  [codex {:keys [clientInfo capabilities]}]
  (request! codex
            "initialize"
            (cond-> {:clientInfo clientInfo}
              (some? capabilities)
              (assoc :capabilities capabilities)))
  (notify! codex "initialized" nil)
  codex)

(defn start
  ([] (start {}))
  ([{:keys [clientInfo capabilities]
     :or {clientInfo {:name "glass"
                      :version "dev"}}}]
   (let [codex (client (start-process))]
     (try
       (initialize! codex {:clientInfo clientInfo
                           :capabilities capabilities})
       codex
       (catch Throwable t
         (close codex)
         (throw t))))))

(defn thread-start
  [codex params]
  (request! codex "thread/start" params))

(defn thread-fork
  [codex params]
  (request! codex "thread/fork" params))

(defn turn-start
  [codex params]
  (with-transport-lock
    codex
    (fn []
      (let [start-result (request! codex "turn/start" params)
            turn-id (get-in start-result [:turn :id])]
        (when-not turn-id
          (throw (ex-info "[openai/codex] turn/start response missing turn id"
                          {:result start-result})))
        (loop [items []]
          (let [message (read-message! codex)
                method (:method message)]
            (cond
              (server-request? message)
              (do
                (handle-server-request! codex message)
                (recur items))

              (response? message)
              (throw (ex-info "[openai/codex] unexpected response while waiting for turn/completed"
                              {:response message}))

              (= "item/completed" method)
              (let [notification-params (:params message)]
                (if (= turn-id (:turnId notification-params))
                  (recur (conj items (:item notification-params)))
                  (recur items)))

              (= "turn/completed" method)
              (let [completed-turn (get-in message [:params :turn])]
                (if (= turn-id (:id completed-turn))
                  {:turn (assoc completed-turn :items items)}
                  (recur items)))

              :else
              (recur items))))))))
