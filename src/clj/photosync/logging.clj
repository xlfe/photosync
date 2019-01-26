(ns photosync.logging
  (:require
    [clojure.tools.logging :as log]
    [cheshire.core :as json])
  (:import [com.google.cloud.logging
            Logging
            LogEntry
            LogEntry$Builder
            Severity
            Payload$StringPayload
            Payload$JsonPayload
            Logging$WriteOption]))




(defn log-entry
 [entry]
 (cond
   (instance? String entry) (.of LogEntry (.of Payload$StringPayload entry))
   (instance? map entry) (.of LogEntry (.of Payload$JsonPayload (json/encode entry)))))

(defn set-log-level
 [level entries]
 (map #(doto (.toBuilder %)
         (.setSeverity level)
         (.build))
      entries))

(defn log
  "logs a message"
  [level & more]
  (.Logging write (set-log-level level (map log-entry more))
                   (. Logging$WriteOption logName "test")))

(defmacro info
  [& args]
  `(log Severity/INFO ~@args))

(defmacro debug
  [& args]
  `(log Severity/DEBUG ~@args))
