(ns glass.service.openai.codex-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [glass.service.openai.codex :as codex]))

(deftest handle-server-request-allows-custom-methods
  (testing "custom request handling is supplied by the caller rather than hardcoded in transport"
    (let [written (atom nil)
          client {:server-request-handler (fn [{:keys [method]}]
                                            (when (= "item/tool/requestUserInput" method)
                                              {:result {:answers {"q" {:text "yes"}}}}))}]
      (with-redefs [glass.service.openai.codex/write-message!
                    (fn [_ message]
                      (reset! written message))]
        (#'glass.service.openai.codex/handle-server-request!
         client
         {:id 7
          :method "item/tool/requestUserInput"
          :params {:questions []}})
        (is (= {:id 7
                :result {:answers {"q" {:text "yes"}}}}
               @written))))))

(deftest handle-server-request-defaults-approval-responses
  (testing "newer approval methods get default deny responses"
    (let [messages (atom [])]
      (with-redefs [glass.service.openai.codex/write-message!
                    (fn [_ message]
                      (swap! messages conj message))]
        (#'glass.service.openai.codex/handle-server-request!
         {:server-request-handler #'glass.service.openai.codex/default-server-request-handler}
         {:id 1
          :method "execCommandApproval"})
        (#'glass.service.openai.codex/handle-server-request!
         {:server-request-handler #'glass.service.openai.codex/default-server-request-handler}
         {:id 2
          :method "applyPatchApproval"})
        (is (= [{:id 1
                 :result {:decision "denied"}}
                {:id 2
                 :result {:decision "denied"}}]
               @messages))))))

(deftest drain-reader-consumes-lines
  (testing "stderr can be drained without touching the JSON transport"
    (let [lines (atom [])
          reader (java.io.BufferedReader.
                  (java.io.StringReader. "first\nsecond\n"))
          drainer (#'glass.service.openai.codex/drain-reader!
                   reader
                   (fn [line]
                     (swap! lines conj line)))]
      @drainer
      (is (= ["first" "second"] @lines)))))

(deftest thread-start-serializes-concurrent-callers
  (testing "a second request does not write until the first request completes"
    (let [client {:id-counter (atom 0)
                  :transport-lock (Object.)}
          first-read-entered (promise)
          release-first-read (promise)
          second-write (promise)
          writes (atom [])
          read-count (atom 0)]
      (with-redefs [glass.service.openai.codex/write-message!
                    (fn [_ message]
                      (let [messages (swap! writes conj message)]
                        (when (= 2 (count messages))
                          (deliver second-write message))))
                    glass.service.openai.codex/read-message!
                    (fn [_]
                      (case (swap! read-count inc)
                        1 (do
                            (deliver first-read-entered true)
                            @release-first-read
                            {:id 1
                             :result {:thread {:id "thread-1"}}})
                        {:id 2
                         :result {:thread {:id "thread-2"}}}))]
        (let [first (future (codex/thread-start client {:name "first"}))
              _ (is (true? (deref first-read-entered 1000 nil)))
              second (future (codex/thread-start client {:name "second"}))]
          (is (nil? (deref second-write 200 nil)))
          (deliver release-first-read true)
          (is (= {:thread {:id "thread-1"}} @first))
          (is (= {:thread {:id "thread-2"}} @second))
          (is (= [{:id 1
                   :method "thread/start"
                   :params {:name "first"}}
                  {:id 2
                   :method "thread/start"
                   :params {:name "second"}}]
                 @writes)))))))

(deftest turn-start-holds-transport-until-complete
  (testing "a turn keeps exclusive access to the transport until turn/completed"
    (let [client {:id-counter (atom 0)
                  :transport-lock (Object.)}
          turn-waiting (promise)
          release-turn (promise)
          second-write (promise)
          writes (atom [])
          read-count (atom 0)]
      (with-redefs [glass.service.openai.codex/write-message!
                    (fn [_ message]
                      (let [messages (swap! writes conj message)]
                        (when (= 2 (count messages))
                          (deliver second-write message))))
                    glass.service.openai.codex/read-message!
                    (fn [_]
                      (case (swap! read-count inc)
                        1 {:id 1
                           :result {:turn {:id "turn-1"}}}
                        2 (do
                            (deliver turn-waiting true)
                            @release-turn
                            {:method "turn/completed"
                             :params {:turn {:id "turn-1"
                                             :status :completed}}})
                        {:id 2
                         :result {:thread {:id "thread-1"}}}))]
        (let [turn (future (codex/turn-start client {:input []}))
              _ (is (true? (deref turn-waiting 1000 nil)))
              thread (future (codex/thread-start client {:name "next"}))]
          (is (nil? (deref second-write 200 nil)))
          (deliver release-turn true)
          (is (= {:turn {:id "turn-1"
                         :status :completed
                         :items []}}
                 @turn))
          (is (= {:thread {:id "thread-1"}} @thread))
          (is (= {:id 2
                  :method "thread/start"
                  :params {:name "next"}}
                 (deref second-write 1000 nil)))
          (is (= [{:id 1
                   :method "turn/start"
                   :params {:input []}}
                  {:id 2
                   :method "thread/start"
                   :params {:name "next"}}]
                 @writes)))))))
