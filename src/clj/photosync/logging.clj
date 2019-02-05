(ns photosync.logging
  (:require
    [clojure.tools.logging :as log]
    [clojure.walk :as walk]
    [cheshire.core :as json])
  (:import [com.google.cloud.logging
            Logging
            LoggingOptions
            LogEntry
            LogEntry$Builder
            Severity
            Payload$StringPayload
            Payload$JsonPayload
            Logging$WriteOption]
           [com.google.appengine.api.modules ModulesService ModulesServiceFactory]
           [com.google.appengine.api.utils SystemProperty SystemProperty$Environment$Value]
           [com.google.apphosting.api ApiProxy]
           [com.google.cloud MonitoredResource]))



(def PRODUCTION
  (= (.value SystemProperty/environment) (SystemProperty$Environment$Value/Production)))


(defn make-log-entry
 [level entry labels]
 (->
     (LogEntry/newBuilder (Payload$StringPayload/of entry))
     (.setSeverity level)
     (.setLabels (walk/stringify-keys labels))
     (.build)))


;(defn
;  request-id
;  []
;  (.get (.getAttributes (.getCurrentEnvironment ApiProxy)) "com.google.appengine.runtime.request_log_id"))


(defn resource
 []
 (let [modules-api (ModulesServiceFactory/getModulesService)
       name (.getCurrentModule modules-api)
       instance (.getCurrentInstanceId modules-api)
       version (.getCurrentVersion modules-api)]
   (-> (MonitoredResource/newBuilder "gae_app")
       (.setLabels {
                      "project_id" (.get SystemProperty/applicationId)
                      "module_id" name
                      "version_id" version
                      "instance_id" instance})
       (.build))))


(def log-name
  "appengine.googleapis.com%2Frequest_log")
 ;(str "projects/" (.get SystemProperty/applicationId) "/logs/appengine.googleapis.com"));%2Frequest_log"

(defn log
  [level & args]
  (let [labels (apply merge (filter #(instance? java.util.Map %) args))
        entry (apply str (filter #(not (instance? java.util.Map %)) args))]
    (if PRODUCTION
      (.write (.getService (LoggingOptions/getDefaultInstance))
              [(make-log-entry level entry labels)]
              (into-array [
                           (Logging$WriteOption/logName log-name)
                           (Logging$WriteOption/resource (resource))]));

      (log/info (str labels entry)))))

(defmacro info
  [& args]
  `(log Severity/INFO ~@args))

(defmacro debug
  [& args]
  `(log Severity/DEBUG ~@args))
