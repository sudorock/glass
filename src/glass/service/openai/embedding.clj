(ns glass.service.openai.embedding
  (:import
   [com.openai.client OpenAIClient]
   [com.openai.models.embeddings CreateEmbeddingResponse Embedding EmbeddingCreateParams EmbeddingModel]))

(defn generate
  [^OpenAIClient client ^String embedding-model ^String input]
  (let [^CreateEmbeddingResponse response
        (-> client
            (.embeddings)
            (.create (-> (EmbeddingCreateParams/builder)
                         (.input input)
                         (.model (EmbeddingModel/of embedding-model))
                         (.build))))
        ^Embedding embedding
        (-> response
            (.data)
            (.get 0))]
    (->> (.embedding embedding)
         (mapv double))))
