(ns glass.mcp-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [glass.json :as json]
   [glass.mcp :as mcp]
   [glass.mcp.protocol :as protocol]
   [glass.mcp.system.http :as http]
   [glass.mcp.system.stdio :as stdio]
   [glass.mcp.system.watch-state :as watch]))

(deftest parse-stream-test
  (testing "parses multiple JSON values from a single stream"
    (let [parsed (with-open [reader (java.io.StringReader. "{\"a\":1}{\"b\":2}")]
                   (doall (json/parse-stream reader)))]
      (is (= [{:a 1} {:b 2}] parsed))))
  (testing "supports non-keywordized keys"
    (let [parsed (with-open [reader (java.io.StringReader. "{\"a\":1}")]
                   (doall (json/parse-stream reader {:keywordize? false})))]
      (is (= [{"a" 1}] parsed)))))

(deftest run-http-test
  (let [calls (atom [])]
    (with-redefs [http/start! (fn [opts]
                                (swap! calls conj [:http opts])
                                :jetty)
                  watch/start! (fn [opts]
                                 (swap! calls conj [:watch opts])
                                 :watch)]
      (mcp/run-http! {:port 4321})
      (is (= [[:http {:port 4321}]
              [:watch {:port 4321}]]
             @calls)))))

(deftest run-stdio-test
  (let [calls (atom [])]
    (with-redefs [stdio/start! (fn []
                                 (swap! calls conj [:stdio])
                                 :stdio)
                  watch/start! (fn [opts]
                                 (swap! calls conj [:watch opts])
                                 :watch)]
      (mcp/run-stdio! {:session :s})
      (is (= [[:stdio]
              [:watch {:session :s}]]
             @calls)))))

(deftest registry-api-test
  (let [initial @mcp/state
        tool-fn (fn [_req _args] {:content [{:type "text" :text "ok"}]
                                  :isError false})]
    (try
      (reset! mcp/state (mcp/initial-state))
      (mcp/add-tool {:name "echo"
                     :title "Echo"
                     :description "Echo tool"
                     :schema {:type "object"}
                     :tool-fn tool-fn})
      (mcp/add-resource {:uri "resource:test"
                         :name "Test"
                         :title "Test resource"
                         :description "A test resource"
                         :mime-type "text/plain"
                         :load-fn (fn [] "hello")})
      (mcp/add-prompt {:name "ask"
                       :title "Ask"
                       :description "Ask prompt"
                       :arguments [{:name "q" :required true}]
                       :messages-fn (fn [{:keys [q]}]
                                      [{:role "user"
                                        :content {:type "text" :text q}}])})
      (mcp/set-instructions "be precise")
      (is (= "Echo" (get-in @mcp/state [:tools "echo" :title])))
      (is (= "resource:test" (-> @mcp/state :resources keys first)))
      (is (= "ask" (-> @mcp/state :prompts keys first)))
      (is (= "be precise" (:instructions @mcp/state)))
      (finally
        (reset! mcp/state initial)))))

(deftest json-rpc-api-test
  (is (= {:jsonrpc "2.0"
          :id 1
          :method "tools/list"}
         (mcp/json-rpc-request 1 "tools/list" nil)))
  (is (= {:jsonrpc "2.0"
          :method "notifications/initialized"}
         (mcp/json-rpc-notification "notifications/initialized")))
  (is (= {:jsonrpc "2.0"
          :id 9
          :result {:ok true}}
         (mcp/json-rpc-response 9 {:ok true})))
  (is (= {:jsonrpc "2.0"
          :id 3
          :error {:code -32602
                  :message "bad input"}}
         (mcp/json-rpc-error 3 {:code -32602 :message "bad input"})))
  (is (= -32700 mcp/json-rpc-parse-error))
  (is (= -32600 mcp/json-rpc-invalid-request))
  (is (= -32601 mcp/json-rpc-method-not-found))
  (is (= -32602 mcp/json-rpc-invalid-params))
  (is (= -32603 mcp/json-rpc-internal-error)))

(deftest protocol-wrapper-test
  (let [calls (atom [])]
    (with-redefs [protocol/request (fn [req]
                                     (swap! calls conj [:request req]))
                  protocol/notify (fn [req]
                                    (swap! calls conj [:notify req]))]
      (mcp/request {:id 1})
      (mcp/notify {:id 2})
      (is (= [[:request {:id 1}]
              [:notify {:id 2}]]
             @calls)))))

(deftest nrepl-namespace-test
  (testing "nrepl helper namespace loads"
    (is (some? (requiring-resolve 'glass.mcp.lib.nrepl-client/connect)))
    (is (some? (requiring-resolve 'glass.mcp.lib.nrepl-client/send-msg)))))
