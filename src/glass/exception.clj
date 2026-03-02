(ns glass.exception)

(defn exception->map
  [^Throwable e]
  (merge {:class (str (class e))
          :message (.getMessage e)
          :stacktrace (mapv str (.getStackTrace e))}
         (when (.getCause e) {:cause (exception->map (.getCause e))})
         (if-let [exception-info-payload (ex-data e)]
           {:exception-info-payload exception-info-payload})))
