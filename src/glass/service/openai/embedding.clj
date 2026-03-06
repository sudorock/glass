(ns glass.service.openai.embedding
  (:require
   [glass.http :as http]
   [glass.json :as json]))

(def ^:private embedding-url "https://api.openai.com/v1/embeddings")

(defn generate
  [api-key embedding-model input-text]
  (let [response (http/post embedding-url
                            {:headers {"Accept" "application/json"
                                       "Content-Type" "application/json"
                                       "Authorization" (str "Bearer " api-key)}
                             :body (json/stringify {:model embedding-model
                                                    :input input-text})
                             :as :text})]
    (if (<= 200 (:status response) 299)
      (get-in (json/parse (:body response)) [:data 0 :embedding])
      (throw (ex-info "[openai] embedding request failed"
                      {:status (:status response)
                       :body (:body response)})))))
