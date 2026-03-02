(ns glass.exception-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [glass.exception :as ex]))

(deftest exception->map-test
  (testing "maps throwable details including cause and ex-data"
    (let [cause (ex-info "cause-message" {:cause true})
          err (ex-info "top-message" {:id 1} cause)
          m (ex/exception->map err)]
      (is (= "class clojure.lang.ExceptionInfo" (:class m)))
      (is (= "top-message" (:message m)))
      (is (vector? (:stacktrace m)))
      (is (= {:id 1} (:exception-info-payload m)))
      (is (= "cause-message" (get-in m [:cause :message])))
      (is (= {:cause true} (get-in m [:cause :exception-info-payload]))))))
