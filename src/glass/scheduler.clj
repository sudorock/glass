(ns glass.scheduler
  (:require
   [chime.core :as chime])
  (:import
   (java.io StringReader)
   (java.time Instant
              LocalDate
              LocalDateTime
              OffsetDateTime
              ZoneId
              ZonedDateTime)
   (java.time.temporal Temporal)
   (net.fortuna.ical4j.data CalendarBuilder)
   (net.fortuna.ical4j.model Calendar Component Recur)
   (net.fortuna.ical4j.model.component VEvent)
   (net.fortuna.ical4j.model.property RRule)))

(set! *warn-on-reflection* true)

(def ^:private ^ZoneId system-zone
  (ZoneId/systemDefault))

(defn- parse-calendar
  [ical-text]
  (try (.build (CalendarBuilder.) (StringReader. ical-text))
       (catch Exception e
         (throw (ex-info "Failed to parse iCalendar text"
                         {:ical-text ical-text}
                         e)))))

(defn- only-vevent
  [^Calendar calendar]
  (let [events (->> (.getComponents calendar)
                    (filter #(= "VEVENT" (.getName ^Component %)))
                    vec)]
    (when-not (= 1 (count events))
      (throw (ex-info "Expected exactly one VEVENT in iCalendar text"
                      {:vevent-count (count events)})))
    (first events)))

(defn- start-temporal
  [^VEvent event]
  (let [start (some-> event
                      .getDateTimeStart
                      .getDate)]
    (when-not start
      (throw (ex-info "VEVENT is missing DTSTART" {})))
    start))

(defn- event-recur
  [^VEvent event]
  (let [^RRule rrule-prop (some-> event
                                  (.getProperty "RRULE")
                                  (.orElse nil))]
    (when rrule-prop
      (.getRecur rrule-prop))))

(defn- temporal->instant
  [temporal]
  (cond
    (instance? Instant temporal) temporal
    (instance? OffsetDateTime temporal)
    (.toInstant ^OffsetDateTime temporal)
    (instance? ZonedDateTime temporal)
    (.toInstant ^ZonedDateTime temporal)
    (instance? LocalDateTime temporal)
    (let [^ZonedDateTime zoned-datetime (.atZone ^LocalDateTime temporal
                                                  ^ZoneId system-zone)]
      (.toInstant zoned-datetime))
    (instance? LocalDate temporal)
    (.toInstant (.atStartOfDay ^LocalDate temporal system-zone))
    :else
    (throw (ex-info "Unsupported temporal value in recurrence"
                    {:temporal-class (str (class temporal))
                     :temporal temporal}))))

(defn- now-like
  [start]
  (let [now (Instant/now)]
    (cond
      (instance? Instant start) now
      (instance? OffsetDateTime start)
      (OffsetDateTime/ofInstant now (.getOffset ^OffsetDateTime start))
      (instance? ZonedDateTime start)
      (ZonedDateTime/ofInstant now (.getZone ^ZonedDateTime start))
      (instance? LocalDateTime start)
      (LocalDateTime/ofInstant now system-zone)
      (instance? LocalDate start)
      (LocalDate/ofInstant now system-zone)
      :else
      (throw (ex-info "Unsupported DTSTART temporal value"
                      {:start-class (str (class start))
                       :start start})))))

(defn- recurrence-instants
  [^Recur recur-value ^Temporal start]
  (letfn [(step [^Temporal after]
            (lazy-seq
             (when-let [^Temporal next-occurrence
                        (.getNextDate ^Recur recur-value start after)]
               (cons (temporal->instant next-occurrence)
                     (step next-occurrence)))))]
    (step (now-like start))))

(defn parse-ical
  [ical-text]
  (let [calendar (parse-calendar ical-text)
        event (only-vevent calendar)
        start (start-temporal event)
        recur-value (event-recur event)
        times (if recur-value
                (recurrence-instants recur-value start)
                [(temporal->instant start)])]
    (chime/without-past-times times)))

(defn schedule
  ([ical-text f] (schedule ical-text f {}))
  ([ical-text f {:keys [on-finished] :as _opts}]
   (chime/chime-at (parse-ical ical-text)
                   f
                   (cond-> {}
                     on-finished (assoc :on-finished on-finished)))))
