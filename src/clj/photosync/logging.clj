(ns photosync.logging
  (:require
    [clojure.tools.logging :as log]
    [clojure.walk :as walk]
    [cheshire.core :as json])
  (:import [com.google.cloud.logging
            Logging
            LoggingOptions
            MonitoredResource
            LogEntry
            LogEntry$Builder
            Severity
            Payload$StringPayload
            Payload$JsonPayload
            Logging$WriteOption]
           [com.google.appengine.api.utils SystemProperty SystemProperty$Environment$Value]
           [com.google.cloud MonitoredResource]))



(def PRODUCTION
  (= (.value SystemProperty/environment) (SystemProperty$Environment$Value/Production)))


(defn make-log-entry
 [level entry]
 (let [payload (cond
                 ;(instance? String entry) (Payload$StringPayload/of entry)
                 (instance? java.util.Map entry) (Payload$JsonPayload/of (walk/stringify-keys entry))
                 :else (Payload$StringPayload/of entry))
       le (LogEntry/newBuilder payload)]
   (.setSeverity le level)
   (.build le)))




(defn log
  [level & more]
  (let [entries (map #(make-log-entry level %) more)]
    (if PRODUCTION
      (.write (.getService (LoggingOptions/getDefaultInstance))
              entries
              (into-array [
                           (Logging$WriteOption/logName "test")
                           (Logging$WriteOption/resource (.build (.newBuilder MonitoredResource "global")))])));

    (log/info (map str entries))))
      ;(log/info (map str entries)))))

(defmacro info
  [& args]
  `(log Severity/INFO ~@args))

(defmacro debug
  [& args]
  `(log Severity/DEBUG ~@args))
