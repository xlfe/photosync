(ns photosync.auth
 (:require
       [om.next :as om]
       [compassus.core :as compassus]))

(set! *warn-on-infer* true)

(def SCOPE
  "https://picasaweb.google.com/data/ profile email openid")
 ;"profile email openid")

(def client-params
  (js-obj "apiKey" "***REMOVED***"
    "client_id" "***REMOVED***.apps.googleusercontent.com"
    ;      "ux_mode" "popup"
    "scope" SCOPE))

(defn google-auth-instance []
 (js-invoke js/gapi.auth2 "getAuthInstance"))

(defn google-current-user []
 (js-invoke (goog.object/get (google-auth-instance) "currentUser") "get"))

(defn sign-in [app]
 (.then (js-invoke (google-auth-instance) "signIn")
  (fn [token]
    (println (str "Logged in token: " token)))
  (fn [error]
    (let [error (or (goog.object/get error "details") "Unknown error")]
      (println (str "Login Error: " error))
      (compassus/set-route! app :login {:params {:error-text error}})))))

(defn get-user-details
  [user]
  (let [profile (js-invoke user "getBasicProfile")]
       {:id (js-invoke profile "getId")
         :name (js-invoke profile "getName")
         :given_name (js-invoke profile "getGivenName")
         :family_name (js-invoke profile "getFamilyName")
         :image_url (js-invoke profile "getImageUrl")
         :email (js-invoke profile "getEmail")}))


(defn google-auth-state-changes [app]
  (fn []
   (if-let [currentUser (google-current-user)]
     (let [isSignedIn (js-invoke currentUser "isSignedIn")
           granted (js-invoke currentUser "hasGrantedScopes" SCOPE)]
      (println (str "Signed in:") isSignedIn)
      (if (not isSignedIn)
        (compassus/set-route! app :login {:params {:error-text "You might have a privacy extension blocking third party cookies"}}))
      (if granted
       (compassus/set-route! app :welcome {:params {:error-text nil :user (get-user-details currentUser)}})))
     (println "Not signed in"))))

(defn google-auth-init
  [app]
  (.then (js-invoke js/gapi.auth2 "init" client-params)
   (fn [GoogleAuth]
     (let [currentUser (goog.object/get GoogleAuth "currentUser")
           state-changes (google-auth-state-changes app)
           signedIn (js-invoke (google-current-user) "isSignedIn")]
      (js-invoke currentUser "listen" state-changes)
      (println (str "SIGNED IN: " signedIn))
      (if signedIn
       (state-changes))))
   (fn [error]
     (let [error (goog.object/get error "details")]
       (println (str "Auth Init Error: " error))
       (compassus/set-route! app :login {:params {:error-text error}})))))


(defn google-auth-load
  [cb]
  (js/gapi.load "client:auth2" cb))


