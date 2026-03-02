(ns glass.timestamp
  "Functions for working with time instants represented in epoch milliseconds."
  (:import
   [java.time DateTimeException Instant LocalDate LocalDateTime OffsetDateTime
    ZoneId ZonedDateTime]
   [java.time.format DateTimeFormatter]
   [java.time.temporal ChronoUnit TemporalUnit]
   (java.util Locale)))

(set! *warn-on-reflection* true)

(def ^:private chrono-units
  {:seconds ChronoUnit/SECONDS
   :minutes ChronoUnit/MINUTES
   :hours ChronoUnit/HOURS
   :days ChronoUnit/DAYS
   :weeks ChronoUnit/WEEKS
   :months ChronoUnit/MONTHS
   :years ChronoUnit/YEARS})

(defn- try-parse
  [fns]
  (loop [[f & rst-fns] fns]
    (when f
      (if-let [parsed (try (f) (catch DateTimeException _ nil))]
        parsed
        (recur rst-fns)))))

(defprotocol Timestamp
  (^Long ->ts [x] [s fmt] [s fmt locale] "Convert to epoch milliseconds"))

(extend-protocol Timestamp
  Long
  (->ts [x] x)

  Integer
  (->ts [x] (long x))

  String
  (->ts
    ([s fmt locale]
     (let [formatter (DateTimeFormatter/ofPattern fmt locale)
           fns [#(OffsetDateTime/parse s formatter)
                #(LocalDateTime/parse s formatter)
                #(LocalDate/parse s formatter)]]
       (if-let [parsed (try-parse fns)]
         (->ts parsed)
         (throw (Exception. ^String
                            (format "Text %s could not be parsed" s))))))
    ([s fmt] (->ts s fmt Locale/ENGLISH)))

  Instant
  (->ts [x] (.toEpochMilli x))

  OffsetDateTime
  (->ts [x] (->ts (.toInstant x)))

  ZonedDateTime
  (->ts [x] (->ts (.toInstant x)))

  LocalDateTime
  (->ts [x] (->ts (.atZone x (ZoneId/of "UTC"))))

  LocalDate
  (->ts [x] (->ts (.atStartOfDay x (ZoneId/of "UTC")))))

(defn now
  []
  (System/currentTimeMillis))

(defn ->str
  "Convert epoch milliseconds to string using the specified format and timezone.
   Usage: (->str '2021-09-06T12:00:00' 'yyyy-MM-dd'T'HH:mm:ss')"
  ([^Long ts fmt] (->str ts fmt "UTC"))
  ([^Long ts fmt tz]
   (let [formatter (DateTimeFormatter/ofPattern fmt)
         instant (Instant/ofEpochMilli ts)
         odt (OffsetDateTime/ofInstant instant (ZoneId/of tz))]
     (.format odt formatter))))

(defn add
  "Add the given amount of units to the timestamp
   Usage: (add 1630925574504 :hours 10)"
  [ts unit value]
  (-> ^Instant (Instant/ofEpochMilli ts)
      (.plus ^Long value ^TemporalUnit (get chrono-units unit))
      .toEpochMilli))

(defn subtract
  "Subtract the given amount of units from the timestamp
   Usage: (subtract 1630925574504 :days 100)"
  [ts unit value]
  (-> ^Instant (Instant/ofEpochMilli ts)
      (.minus ^Long value ^TemporalUnit (get chrono-units unit))
      .toEpochMilli))
