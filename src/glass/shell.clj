(ns glass.shell
  (:require
   [clojure.java.io :as io])
  (:import
   [java.lang ProcessBuilder]
   [java.io StringWriter]
   [clojure.lang IDeref IBlockingDeref]
   [java.util.concurrent TimeUnit]
   [java.nio.charset Charset]))

(defprotocol Command
  (std-out [process])
  (std-in [process])
  (std-err [process])
  (alive? [process]))

(defn- stream->string
  ([in]
   (stream->string in (.name (Charset/defaultCharset))))
  ([in enc]
   (with-open [out (StringWriter.)]
     (io/copy in out :encoding enc)
     (.toString out))))

(defn- result
  [^Process process]
  (with-open [std-out (.getInputStream process)
              std-err (.getErrorStream process)]
    {:exit (.exitValue process)
     :out  (stream->string std-out)
     :err  (stream->string std-err)}))

(defn cmd
  [command]
  (let [command          (into-array command)
        ^Process process (.start (ProcessBuilder. ^"[Ljava.lang.String;" command))]
    (reify
      Command
      (std-out [_] (.getInputStream process))
      (std-in [_] (.getOutputStream process))
      (std-err [_] (.getErrorStream process))
      (alive? [_] (.isAlive process))
      IDeref
      (deref [_]
        (.waitFor process)
        (result process))
      IBlockingDeref
      (deref [_ timeout-ms timeout-val]
        (let [exited-before-timeout? (.waitFor process timeout-ms TimeUnit/MILLISECONDS)]
          (if exited-before-timeout?
            (result process)
            (do (.destroyForcibly process)
                timeout-val)))))))
