(ns glass.service.qdrant
  (:import
   [com.google.common.util.concurrent ListenableFuture]
   [io.qdrant.client PointIdFactory QdrantClient QdrantGrpcClient QdrantGrpcClient$Builder ValueFactory VectorsFactory WithPayloadSelectorFactory]
   [io.qdrant.client.grpc Collections$Distance Collections$PayloadSchemaType Collections$VectorParams Collections$VectorParams$Builder]
   [io.qdrant.client.grpc Common$Filter Common$PointId]
   [io.qdrant.client.grpc JsonWithInt$Value]
   [io.qdrant.client.grpc Points$PointStruct Points$PointStruct$Builder Points$RetrievedPoint Points$ScoredPoint Points$ScrollPoints Points$ScrollPoints$Builder Points$ScrollResponse Points$SearchPoints Points$SearchPoints$Builder]
   [java.time Duration]
   [java.util List]))

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
    (ValueFactory/value ^java.util.Map (->payload x))

    :else
    (throw (ex-info "Unsupported Qdrant payload value"
                    {:value x
                     :type (type x)}))))

(defn- ->point-id
  ^Common$PointId
  [x]
  (if (integer? x)
    (PointIdFactory/id (long x))
    (.build
     (doto (Common$PointId/newBuilder)
       (.setUuid (str x))))))

(defn- ->point
  [{:keys [id vector payload]}]
  (let [^Points$PointStruct$Builder builder (Points$PointStruct/newBuilder)]
    (.setId builder (->point-id id))
    (.setVectors builder (VectorsFactory/vectors ^List (->float-vector vector)))
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
  [{:keys [host port tls? api-key timeout-ms]}]
  (let [^QdrantGrpcClient$Builder builder
        (cond-> (QdrantGrpcClient/newBuilder ^String host (int port) (boolean tls?))
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

(defn create-collection
  [^QdrantClient client {:keys [collection-name vector-size] :as opts}]
  (let [^Collections$VectorParams$Builder builder (Collections$VectorParams/newBuilder)]
    (.setDistance builder (distance (:distance opts)))
    (.setSize builder (long vector-size))
    (wait (.createCollectionAsync client ^String collection-name (.build builder))))
  true)

(defn create-payload-index
  [^QdrantClient client {:keys [collection-name field] :as opts}]
  (wait (.createPayloadIndexAsync
         client
         collection-name
         (field-name field)
         (schema-type (:schema-type opts))
         nil
         nil
         nil
         nil))
  true)

(defn upsert-points
  [^QdrantClient client {:keys [collection-name points]}]
  (wait (.upsertAsync client ^String collection-name ^List (mapv ->point points)))
  true)

(defn search-points
  [^QdrantClient client {:keys [collection-name vector filter limit]}]
  (let [^Points$SearchPoints$Builder builder (Points$SearchPoints/newBuilder)]
    (.setCollectionName builder ^String collection-name)
    (.setWithPayload builder (WithPayloadSelectorFactory/enable true))
    (.addAllVector builder ^Iterable (->float-vector vector))
    (when filter
      (.setFilter builder ^Common$Filter filter))
    (.setLimit builder (long limit))
    (mapv scored-point->clj
          (wait (.searchAsync client (.build builder))))))

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
