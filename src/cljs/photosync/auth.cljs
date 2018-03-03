(ns photosync.auth
 (:require
       [om.next :as om]
       [compassus.core :as compassus]))

(set! *warn-on-infer* true)

(def SCOPE
  ;"https://picasaweb.google.com/data/ profile email openid")
 "profile email openid")

(def client-params
  (js-obj "apiKey" "***REMOVED***"
    "client_id" "***REMOVED***.apps.googleusercontent.com"
    ;"prompt" "none"
    ;      "ux_mode" "popup"
    "scope" SCOPE))


(defn google-auth-instance []
 (.getAuthInstance js/gapi.auth2))

(defn google-current-user []
 (.get (goog.object/get (google-auth-instance) "currentUser")))

(defn sign-in []
 ;(.signIn (google-auth-instance)))
 (.then (.grantOfflineAccess (google-auth-instance) client-params)
  (fn [token] (println (str "Offline token: " token)))))



(defn google-auth-state-changes [app]
  (fn []
   (let [currentUser (google-current-user)
         granted (js-invoke currentUser "hasGrantedScopes" SCOPE)
         profile (.getBasicProfile currentUser)]
    (println (str "Current user:" currentUser))
    (println (str "Profile:" profile))
    (if granted
     (println (str "Granted scope:" granted))
     (compassus/set-route! app :login)))))

(defn google-auth-init-complete [])


(defn google-auth-init
  [app]
  (.then (.init js/gapi.client client-params)
   (fn []
     (let [currentUser (goog.object/get (google-auth-instance) "currentUser")
           state-changes (google-auth-state-changes app)]
      (.listen currentUser state-changes)))
   (fn [error]
    (println error))))


(defn google-auth-load
  [cb]
  (js/gapi.load "client:auth2" cb))


