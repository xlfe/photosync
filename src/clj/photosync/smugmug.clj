(ns photosync.smugmug
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


(require ['oauth.client :as 'oauth])

;; Create a Consumer, in this case one to access Twitter.
;; Register an application at Twitter (https://dev.twitter.com/apps/new)
;; to obtain a Consumer token and token secret.
(def consumer (oauth/make-consumer "***REMOVED***"
                                   "***REMOVED***"
                                   "https://api.twitter.com/oauth/request_token"
                                   "https://api.twitter.com/oauth/access_token"
                                   "https://api.twitter.com/oauth/authorize"
                                   :hmac-sha1))



(def REDIRECT_URI
  (let [env (.value SystemProperty/environment)
        uri (if (= env (SystemProperty$Environment$Value/Production))
             "https://photosync.net/smugmug_callback"
             "http://localhost:8080/smugmug_callback")]
    uri))


(defn smugmug-redirect
  (let [request-token (oauth/request-token consumer REDIRECT_URI)]
     (oauth/user-approval-uri consumer
                         (:oauth_token request-token))))


(defn smugmug-callback
 [req]
 (log/info (:uri req)))



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



(defroutes auth-routes
 (GET "/getsmug" [] (redirect (smugmug-redirect) :temporary-redirect))
 (GET "/smugmug_callback" [] smugmug-callback))


(defn add-auth [app-routes error-routes extra]
  (->
   (compojure.core/routes app-routes auth-routes error-routes)
   auth-user
   ;no-cache
   (wrap-session {
                  :store (cookie-store cookie-key)
                  :cookie-name "S"
                  :cookie-attrs (merge {:max-age 3600 :http-only true} extra)})))

