(ns glass.service.qdrant
  (:import
   [com.google.common.util.concurrent ListenableFuture]
   [io.grpc ManagedChannel ManagedChannelBuilder]
   [io.qdrant.client PointIdFactory QdrantClient QdrantGrpcClient QdrantGrpcClient$Builder QueryFactory ValueFactory VectorFactory VectorsFactory WithPayloadSelectorFactory]
   [io.qdrant.client.grpc Collections$CreateCollection Collections$CreateCollection$Builder Collections$Distance Collections$Modifier Collections$PayloadIndexParams Collections$PayloadIndexParams$Builder Collections$PayloadSchemaType Collections$SparseVectorConfig Collections$SparseVectorConfig$Builder Collections$SparseVectorParams Collections$SparseVectorParams$Builder Collections$TextIndexParams Collections$TextIndexParams$Builder Collections$TokenizerType Collections$VectorParams Collections$VectorParams$Builder Collections$VectorParamsMap Collections$VectorParamsMap$Builder Collections$VectorsConfig Collections$VectorsConfig$Builder]
   [io.qdrant.client.grpc Common$Filter Common$PointId]
   [io.qdrant.client.grpc JsonWithInt$Value]
   [io.qdrant.client.grpc Points$Document Points$Document$Builder Points$Fusion Points$PointStruct Points$PointStruct$Builder Points$PointsIdsList Points$PointsIdsList$Builder Points$PointsSelector Points$PointsSelector$Builder Points$PrefetchQuery Points$PrefetchQuery$Builder Points$Query Points$QueryPoints Points$QueryPoints$Builder Points$RetrievedPoint Points$Rrf Points$Rrf$Builder Points$ScoredPoint Points$ScrollPoints Points$ScrollPoints$Builder Points$ScrollResponse Points$SetPayloadPoints Points$SetPayloadPoints$Builder Points$Vector]
   [java.time Duration]
   [java.util List Map]
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)

(defn- wait
  [^ListenableFuture future]
  (.get future))

(defn- distance
  [x]
  (case x
    :cosine Collections$Distance/Cosine
    :dot Collections$Distance/Dot
    :euclid Collections$Distance/Euclid
    :manhattan Collections$Distance/Manhattan))

(defn- schema-type
  [x]
  (case x
    :keyword Collections$PayloadSchemaType/Keyword
    :integer Collections$PayloadSchemaType/Integer
    :float Collections$PayloadSchemaType/Float
    :bool Collections$PayloadSchemaType/Bool
    :geo Collections$PayloadSchemaType/Geo
    :text Collections$PayloadSchemaType/Text
    :datetime Collections$PayloadSchemaType/Datetime
    :uuid Collections$PayloadSchemaType/Uuid))

(defn- modifier
  [x]
  (case x
    :idf Collections$Modifier/Idf
    :none Collections$Modifier/None))

(defn- tokenizer
  [x]
  (case x
    :word Collections$TokenizerType/Word
    :whitespace Collections$TokenizerType/Whitespace
    :prefix Collections$TokenizerType/Prefix
    :multilingual Collections$TokenizerType/Multilingual))

(defn- field-name
  [x]
  (if (keyword? x)
    (name x)
    (str x)))

(declare ->value)

(defn- ->float-vector
  [xs]
  (mapv float xs))

(defn- ->payload
  [m]
  (into {}
        (map (fn [[k v]]
               [(field-name k) (->value v)]))
        m))

(defn- ->value
  [x]
  (cond
    (nil? x)
    (ValueFactory/nullValue)

    (string? x)
    (ValueFactory/value ^String x)

    (boolean? x)
    (ValueFactory/value (boolean x))

    (integer? x)
    (ValueFactory/value (long x))

    (number? x)
    (ValueFactory/value (double x))

    (vector? x)
    (ValueFactory/value ^List (mapv ->value x))

    (map? x)
    (ValueFactory/value ^Map (->payload x))

    (uuid? x)
    (ValueFactory/value (str x))

    :else
    (throw (ex-info "Unsupported Qdrant payload value"
                    {:value x
                     :type (type x)}))))

(defn point-id
  ^Common$PointId
  [x]
  (cond
    (integer? x)
    (PointIdFactory/id (long x))

    (uuid? x)
    (.build
     (doto (Common$PointId/newBuilder)
       (.setUuid (str x))))

    (string? x)
    (.build
     (doto (Common$PointId/newBuilder)
       (.setUuid x)))

    :else
    (throw (ex-info "Unsupported Qdrant point ID"
                    {:value x
                     :type (type x)}))))

(defn- ->document
  ^Points$Document
  [{:keys [text model options]}]
  (let [^Points$Document$Builder builder (Points$Document/newBuilder)]
    (.setText builder ^String text)
    (.setModel builder ^String model)
    (when (some? options)
      (.putAllOptions builder (->payload options)))
    (.build builder)))

(defn- ->vector
  ^Points$Vector
  [v]
  (cond
    (sequential? v)
    (VectorFactory/vector ^List (->float-vector v))

    (map? v)
    (VectorFactory/vector ^Points$Document (->document v))

    :else
    (throw (ex-info "Unsupported Qdrant vector"
                    {:value v
                     :type (type v)}))))

(defn- ->point
  [{:keys [id vectors payload]}]
  (let [^Points$PointStruct$Builder builder (Points$PointStruct/newBuilder)
        named (into {}
                    (map (fn [[k v]]
                           [(field-name k) (->vector v)]))
                    vectors)]
    (.setId builder (point-id id))
    (.setVectors builder (VectorsFactory/namedVectors ^Map named))
    (when (some? payload)
      (.putAllPayload builder (->payload payload)))
    (.build builder)))

(defn- value->clj
  [^JsonWithInt$Value value]
  (case (str (.getKindCase value))
    "NULL_VALUE" nil
    "STRING_VALUE" (.getStringValue value)
    "INTEGER_VALUE" (.getIntegerValue value)
    "DOUBLE_VALUE" (.getDoubleValue value)
    "BOOL_VALUE" (.getBoolValue value)
    "LIST_VALUE" (mapv value->clj (.getValuesList (.getListValue value)))
    "STRUCT_VALUE" (into {}
                         (map (fn [[k v]]
                                [k (value->clj v)]))
                         (.getFieldsMap (.getStructValue value)))))

(defn- point-id->clj
  [^Common$PointId point-id]
  (if (.hasNum point-id)
    (.getNum point-id)
    (.getUuid point-id)))

(defn- payload->clj
  [payload]
  (into {}
        (map (fn [[k v]]
               [k (value->clj v)]))
        payload))

(defn- scored-point->clj
  [^Points$ScoredPoint point]
  {:id (point-id->clj (.getId point))
   :score (.getScore point)
   :payload (payload->clj (.getPayloadMap point))})

(defn- retrieved-point->clj
  [^Points$RetrievedPoint point]
  {:id (point-id->clj (.getId point))
   :payload (payload->clj (.getPayloadMap point))})

(defn ^QdrantClient init
  [{:keys [host port tls api-key timeout-ms]}]
  (let [^ManagedChannel channel
        (-> (ManagedChannelBuilder/forAddress ^String host (int port))
            (cond->
              (not tls) (.usePlaintext)
              tls (.useTransportSecurity))
            (.keepAliveTime 60 TimeUnit/SECONDS)
            (.keepAliveTimeout 20 TimeUnit/SECONDS)
            (.keepAliveWithoutCalls true)
            (.build))
        ^QdrantGrpcClient$Builder builder
        (cond-> (QdrantGrpcClient/newBuilder channel true)
          api-key
          (.withApiKey api-key)

          timeout-ms
          (.withTimeout (Duration/ofMillis (long timeout-ms))))]
    (QdrantClient. (.build builder))))

(defn close
  [^QdrantClient client]
  (.close client)
  nil)

(defn collection-exists?
  [^QdrantClient client {:keys [collection-name]}]
  (boolean (wait (.collectionExistsAsync client collection-name))))

(defn- ->vector-params
  ^Collections$VectorParams
  [{:keys [size] :as opts}]
  (let [^Collections$VectorParams$Builder builder (Collections$VectorParams/newBuilder)]
    (.setSize builder (long size))
    (.setDistance builder (distance (:distance opts)))
    (.build builder)))

(defn- ->sparse-vector-params
  ^Collections$SparseVectorParams
  [opts]
  (let [^Collections$SparseVectorParams$Builder builder (Collections$SparseVectorParams/newBuilder)]
    (.setModifier builder (modifier (:modifier opts)))
    (.build builder)))

(defn- ->text-index-params
  ^Collections$TextIndexParams
  [{:keys [min-token-len max-token-len lowercase ascii-folding] :as opts}]
  (let [^Collections$TextIndexParams$Builder builder (Collections$TextIndexParams/newBuilder)]
    (when (:tokenizer opts) (.setTokenizer builder (tokenizer (:tokenizer opts))))
    (when min-token-len (.setMinTokenLen builder (int min-token-len)))
    (when max-token-len (.setMaxTokenLen builder (int max-token-len)))
    (when (some? lowercase) (.setLowercase builder (boolean lowercase)))
    (when (some? ascii-folding) (.setAsciiFolding builder (boolean ascii-folding)))
    (.build builder)))

(defn- ->payload-index-params
  ^Collections$PayloadIndexParams
  [{:keys [text]}]
  (when text
    (let [^Collections$PayloadIndexParams$Builder builder (Collections$PayloadIndexParams/newBuilder)]
      (.setTextIndexParams builder (->text-index-params text))
      (.build builder))))

(defn create-collection
  [^QdrantClient client {:keys [collection-name vectors sparse-vectors]}]
  (let [^Collections$VectorParamsMap$Builder vpm (Collections$VectorParamsMap/newBuilder)
        ^Collections$VectorsConfig$Builder vc (Collections$VectorsConfig/newBuilder)
        ^Collections$SparseVectorConfig$Builder svc (Collections$SparseVectorConfig/newBuilder)
        ^Collections$CreateCollection$Builder builder (Collections$CreateCollection/newBuilder)]
    (doseq [[k params] vectors]
      (.putMap vpm (field-name k) (->vector-params params)))
    (.setParamsMap vc (.build vpm))
    (doseq [[k params] sparse-vectors]
      (.putMap svc (field-name k) (->sparse-vector-params params)))
    (.setCollectionName builder ^String collection-name)
    (.setVectorsConfig builder (.build vc))
    (.setSparseVectorsConfig builder (.build svc))
    (wait (.createCollectionAsync client ^Collections$CreateCollection (.build builder))))
  true)

(defn create-payload-index
  [^QdrantClient client {:keys [collection-name field] :as opts}]
  (wait (.createPayloadIndexAsync
         client
         collection-name
         (field-name field)
         (schema-type (:schema-type opts))
         (->payload-index-params opts)
         nil
         nil
         nil))
  true)

(defn upsert-points
  [^QdrantClient client {:keys [collection-name points]}]
  (wait (.upsertAsync client ^String collection-name ^List (mapv ->point points)))
  true)

(defn delete-points
  [^QdrantClient client {:keys [collection-name point-ids filter]}]
  (if filter
    (wait (.deleteAsync client ^String collection-name ^Common$Filter filter))
    (wait (.deleteAsync client ^String collection-name ^List (mapv point-id point-ids))))
  true)

(defn set-payload
  [^QdrantClient client {:keys [collection-name payload point-ids]}]
  (let [^Points$SetPayloadPoints$Builder builder (Points$SetPayloadPoints/newBuilder)
        ^Points$PointsSelector$Builder points-selector-builder (Points$PointsSelector/newBuilder)
        ^Points$PointsIdsList$Builder points-ids-list-builder (Points$PointsIdsList/newBuilder)]
    (.setCollectionName builder ^String collection-name)
    (.setWait builder true)
    (.putAllPayload builder (->payload payload))
    (.addAllIds points-ids-list-builder ^Iterable (mapv point-id point-ids))
    (.setPoints points-selector-builder (.build points-ids-list-builder))
    (.setPointsSelector builder (.build points-selector-builder))
    (wait (.setPayloadAsync client (.build builder) nil))
    true))

(defn- ->query
  ^Points$Query
  [q]
  (cond
    (sequential? q)
    (QueryFactory/nearest ^List (->float-vector q))

    (map? q)
    (QueryFactory/nearest ^Points$Document (->document q))

    :else
    (throw (ex-info "Unsupported Qdrant query"
                    {:value q
                     :type (type q)}))))

(defn- ->prefetch
  ^Points$PrefetchQuery
  [{:keys [using query filter limit]}]
  (let [^Points$PrefetchQuery$Builder builder (Points$PrefetchQuery/newBuilder)]
    (.setUsing builder ^String using)
    (.setQuery builder (->query query))
    (when filter
      (.setFilter builder ^Common$Filter filter))
    (.setLimit builder (long limit))
    (.build builder)))

(defn- ->fusion-query
  ^Points$Query
  [fusion]
  (cond
    (= fusion :rrf)
    (QueryFactory/fusion Points$Fusion/RRF)

    (= fusion :dbsf)
    (QueryFactory/fusion Points$Fusion/DBSF)

    (and (map? fusion) (contains? fusion :rrf))
    (let [{:keys [k weights]} (:rrf fusion)
          ^Points$Rrf$Builder builder (Points$Rrf/newBuilder)]
      (when (some? k)
        (.setK builder (int k)))
      (when (seq weights)
        (.addAllWeights builder ^Iterable (mapv float weights)))
      (QueryFactory/rrf (.build builder)))

    :else
    (throw (ex-info "Unsupported Qdrant fusion"
                    {:value fusion
                     :type (type fusion)}))))

(defn query-points
  [^QdrantClient client {:keys [collection-name prefetch fusion limit]}]
  (let [^Points$QueryPoints$Builder builder (Points$QueryPoints/newBuilder)]
    (.setCollectionName builder ^String collection-name)
    (doseq [p prefetch]
      (.addPrefetch builder (->prefetch p)))
    (.setQuery builder (->fusion-query fusion))
    (.setLimit builder (long limit))
    (.setWithPayload builder (WithPayloadSelectorFactory/enable true))
    (mapv scored-point->clj
          (wait (.queryAsync client ^Points$QueryPoints (.build builder))))))

(defn scroll-points
  [^QdrantClient client {:keys [collection-name filter limit]}]
  (let [^Points$ScrollPoints$Builder builder (Points$ScrollPoints/newBuilder)]
    (.setCollectionName builder ^String collection-name)
    (.setWithPayload builder (WithPayloadSelectorFactory/enable true))
    (.setLimit builder (int limit))
    (when filter
      (.setFilter builder ^Common$Filter filter))
    (mapv retrieved-point->clj
          (.getResultList
           ^Points$ScrollResponse
           (wait (.scrollAsync client (.build builder)))))))
