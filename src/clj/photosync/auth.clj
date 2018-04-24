(ns photosync.auth
  (:use compojure.core)
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]

            [ring.util.response :refer [redirect response]]
            [compojure.core :refer [defroutes ANY GET POST]]

            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.util.response :as response]
            [clojure.java.io :as io]

            [cheshire.core :as parse]
            [hyperion.api :as ds]
            [java-time.temporal :refer [instant]]
            [java-time.core :refer [plus]]
            [java-time.pre-java8 :refer [java-date]]
            [java-time.amount :refer [seconds]]
            [photosync.model :as models]
            [photosync.util :as util])
  (:import (com.google.appengine.api.utils SystemProperty SystemProperty$Environment$Value)))


(def login-uri "https://accounts.google.com")
(def CLIENT_ID "***REMOVED***.apps.googleusercontent.com")
(def CLIENT_SECRET "***REMOVED***")

(def REDIRECT_URI
  (let [env (.value SystemProperty/environment)
        uri (if (= env (SystemProperty$Environment$Value/Production))
             "https://photosync.net/oauth2callback"
             "http://localhost:8080/oauth2callback")]
    ;(log/info env)
    ;(log/info (SystemProperty$Environment$Value/Production))
    ;(log/info uri)
    uri))


(def SCOPES "https://picasaweb.google.com/data/ openid email profile")

;https://developers.google.com/picasa-web/docs/3.0/developers_guide_protocol
;https://developers.google.com/identity/protocols/OpenIDConnect#server-flow
;https://developers.google.com/identity/protocols/CrossClientAuth

(def google-token-endpoint "https://www.googleapis.com/oauth2/v4/token")

(defn gauth-redirect
  [need-refresh]
  (str
    "https://accounts.google.com/o/oauth2/auth?"
    "access_type=offline&"
    "scope=" (ring.util.codec/url-encode SCOPES) "&"
    "redirect_uri=" (ring.util.codec/url-encode REDIRECT_URI) "&"
    "response_type=code&"
    "client_id=" (ring.util.codec/url-encode CLIENT_ID) "&"
    (if need-refresh
      "prompt=consent"
      "prompt=none")))



(defn google-get-token
  [params]
  (let [form-params (assoc
                      params
                      :client_id CLIENT_ID :client_secret CLIENT_SECRET :redirect_uri REDIRECT_URI)]
    (parse/parse-string (:body
                          (client/post google-token-endpoint {:form-params form-params})))))

(defn google-user-details
  [access-token]
  (parse/parse-string (:body
                        (client/get
                          (str
                              "https://www.googleapis.com/oauth2/v1/userinfo?access_token="
                              access-token)))
                      true)) ;;get keywords back...

(defn make-session [gid]
  (:key (ds/save (models/user-session {:googleuser-key gid}))))


(defn get-session [key]
  (ds/find-by-key key))



(defn save-or-get-google-user
  "Get the :key for the google user record (or create it if it doesn't exist)"
  [user-details]
  (if-let [user-record (first (ds/find-by-kind :google-user :filters [:= :id (:id user-details)]))]
    (:key (ds/save (util/safe-merge user-record user-details)))
    (:key (ds/save {:kind :google-user} user-details))))

(defn save-or-update-oauth
  [details]
  (if-let [record (first (ds/find-by-kind :oauth-token :filters [[:= :owner (:owner details)][:= :source (:source details)]]))]
    (:key (ds/save (util/safe-merge record details)))
    (:key (ds/save {:kind :oauth-token} details))))

(defn google-complete-flow
   [code]
   (let [access-token-response (google-get-token {:code code :grant_type "authorization_code"})
         access-token (get access-token-response "access_token")
         refresh-token (get access-token-response "refresh_token")
         expires (get access-token-response "expires_in")
         user-details (google-user-details access-token)
         googleuser-key (save-or-get-google-user user-details)
         token-key (save-or-update-oauth  {:owner googleuser-key
                                           :access_token access-token
                                           :source "google"
                                           :refresh_token refresh-token
                                           :expires (java-date (plus (instant) (seconds expires)))})
         session-key (make-session googleuser-key)]


    ;(log/info (str "access-token: " access-token))
    ;(log/info (str "refresh-token: " refresh-token))
    ;(log/info (str "expires " expires))
    ;(log/info (str "user-details " user-details))
    ;(log/info (str "google-user-key " googleuser-key))
    ;(log/info (str "token-key " token-key))
    (assoc-in (redirect "/") [:session :identity] session-key)))


; If user doesn't consent, redirect to a message
; If user does consent, complete the flow
(defn google-callback [req]
  (if-let [code (get (:params (ring.middleware.params/params-request req)) "code")]
    (google-complete-flow code)
    (redirect "/login-failed")))



(defn add-user [handler]
  (fn [req]
    (let [session-key (get-in req [:session :identity])
          session (get-session session-key)
          google-details (ds/find-by-key (:googleuser-key session))]
      (handler (assoc-in req [:user-details] google-details)))))


(defroutes auth-routes
 ;Simply login to the app
 (GET "/login" [] (redirect (gauth-redirect false)))
 (GET "/authorise" [] (redirect (gauth-redirect true)))
 (GET "/oauth2callback" [] google-callback))

(defn add-auth [app extra]
  (->
   (compojure.core/routes auth-routes app)
   add-user
   (wrap-session {
                  :store (cookie-store "s090DMJ90iosahiosuisdfaweSOMEoBiy}+y{JolJK%1/)F")
                  :cookie-name "S"
                  :cookie-attrs (merge {:max-age 3600 :http-only true} extra)})))

(defn debug-user-auth [handler]
  (fn [request]
    (if (get-in request [:session :identity])
     (handler request)
     (do
       (log/info (str "Debug user needs session!"))
       (let [guk (save-or-get-google-user {
                                           :given_name "Debug"
                                           :email "debug@localhost.com"
                                           :locale "AU"
                                           :name "Debug User"
                                           :family_name "User"
                                           :id 12345
                                           :gender "male"
                                           :verified_email true})]

        (assoc-in (redirect (get-in request [:uri])) [:session :identity] (make-session guk)))))))

