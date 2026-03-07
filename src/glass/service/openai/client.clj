(ns glass.service.openai.client
  (:import
   [com.openai.client OpenAIClient]
   [com.openai.client.okhttp OpenAIOkHttpClient]))

(defn init
  ^OpenAIClient [^String api-key]
  (-> (OpenAIOkHttpClient/builder)
      (.apiKey api-key)
      (.build)))
