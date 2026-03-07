(ns glass.service.openai.codex-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [glass.service.openai.codex :as codex]))

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
