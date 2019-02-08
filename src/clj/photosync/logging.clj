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
            Operation
            Payload$StringPayload
            Payload$JsonPayload
            Logging$WriteOption]
           [org.apache.commons.codec.binary Hex]

           [com.google.appengine.api.modules ModulesService ModulesServiceFactory]
           [com.google.appengine.api.utils SystemProperty SystemProperty$Environment$Value]
           [com.google.apphosting.api ApiProxy]
           [com.google.apphosting.api CloudTrace CloudTraceContext]
           [com.google.cloud MonitoredResource]))



(def PRODUCTION (= (.value SystemProperty/environment) (SystemProperty$Environment$Value/Production)))

(defn get-trace-id []
 (if-let [btid (.getTraceId (CloudTrace/getCurrentContext (ApiProxy/getCurrentEnvironment)))]
   (str
     (Hex/encodeHexString (bytes (byte-array (reverse (java.util.Arrays/copyOfRange btid 1 9)))))
     (Hex/encodeHexString (bytes (byte-array (reverse (java.util.Arrays/copyOfRange btid 10 18))))))
   "unknown"))

(defn compose-trace-id [] (str "projects/" (.get SystemProperty/applicationId) "/traces/" (get-trace-id)))


(defn make-log-entry
  [level entry]
  (->
    (LogEntry/newBuilder (Payload$StringPayload/of entry))
    (.setTrace (compose-trace-id))
    (.setSeverity level)
    (.build)))

(defn get-zone [] (get (.getAttributes (ApiProxy/getCurrentEnvironment)) "com.google.apphosting.api.ApiProxy.datacenter"))

(defn resource
  []
  (let [modules-api (ModulesServiceFactory/getModulesService)
        name (.getCurrentModule modules-api)
        instance (.getCurrentInstanceId modules-api)
        version (.getCurrentVersion modules-api)]
    (-> (MonitoredResource/newBuilder "gae_app")
        (.setLabels {
                     "project_id"  (.get SystemProperty/applicationId)
                     "module_id"   name
                     "version_id"  version
                     "zone" (get-zone)
                     "instance_id" instance})
        (.build))))


(def log-name "appengine.googleapis.com%2Fapp_log")

(defn log
  [level & args]
  (let [labels (apply merge (filter #(instance? java.util.Map %) args))
        entry (apply str (filter #(not (instance? java.util.Map %)) args))
        attrs (get-trace-id)]
    (if PRODUCTION
      (.write (.getService (LoggingOptions/getDefaultInstance))
              [(make-log-entry level entry)]
              (into-array [
                           (Logging$WriteOption/logName log-name)
                           (Logging$WriteOption/labels (reduce-kv #(assoc %1 (name %2) (str %3)) {} labels))
                           (Logging$WriteOption/resource (resource))])) ;

      (log/info (str attrs labels entry)))))

(defmacro info
  [& args]
  `(log Severity/INFO ~@args))

(defmacro debug
  [& args]
  `(log Severity/DEBUG ~@args))
