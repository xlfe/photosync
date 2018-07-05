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
  ([need-refresh path]
   (str
    "https://accounts.google.com/o/oauth2/auth?"
    "access_type=offline&"
    "scope=" (ring.util.codec/url-encode SCOPES) "&"
    "redirect_uri=" (ring.util.codec/url-encode REDIRECT_URI) "&"
    "response_type=code&"
    "client_id=" (ring.util.codec/url-encode CLIENT_ID) "&"
    (if need-refresh
      "prompt=consent"
      "prompt=none")
    (if path
      (str "&state=" path))))
  ([need-refresh]
   (gauth-redirect need-refresh nil)))



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

;(defn make-session [gid]
;  (:key (ds/save (models/user-session {:googleuser-key gid}))))





(defn save-or-get-google-user
  "Get the :key for the google user record (or create it if it doesn't exist)"
  [user-details]
  (if-let [user-record (first (ds/find-by-kind :google-user :filters [:= :id (:id user-details)]))]
    (:key (ds/save (util/safe-merge user-record user-details)))
    (:key (ds/save {:kind :google-user} user-details))))

(defn google-complete-flow
   [code]
   (let [access-token-response (google-get-token {:code code :grant_type "authorization_code"})
         access-token (get access-token-response "access_token")
         refresh-token (get access-token-response "refresh_token")
         expires (get access-token-response "expires_in")
         user-details (google-user-details access-token)
         googleuser-key (save-or-get-google-user user-details)
         token-key (models/save-or-update :oauth-token
                                          [[:= :owner googleuser-key] [:= :source "google"]]
                                          {:owner googleuser-key
                                           :source "google"
                                           :access_token access-token
                                           :refresh_token refresh-token
                                           :expires (java-date (plus (instant) (seconds expires)))})
         session-key (:key (ds/save {:kind :user-session :googleuser-key googleuser-key}))]


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
  (log/info (:params (ring.middleware.params/params-request req)))
  (if-let [code (get (:params (ring.middleware.params/params-request req)) "code")]
    (google-complete-flow code)
    (if-let [error (get (:params (ring.middleware.params/params-request req)) "error")]
      (if
        (= error "immediate_failed")
        (redirect "/authorise")
        (redirect "/login-failed")))))



(defroutes auth-routes
 (GET "/login" [] (redirect (gauth-redirect false) :temporary-redirect))
 (GET "/authorise" [] (redirect (gauth-redirect true) :temporary-redirect))
 (GET "/oauth2callback" [] google-callback))

(defn no-cache [handler]
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:headers "Cache-Control"] "no-cache"))))

(defn no-session
  "The request has no session
   Save the URI requested into a new session and provide a cookie"
 [req]
 (let [session (:key (ds/save {:kind :user-session :last-uri (:uri req)}))]
  (assoc-in (redirect "/login" :temporary-redirect) [:session :identity] session)))

(defn check-user
  "The request has an associated session"
  [handler req session]
  (let [{:keys [:googleuser-key :last-uri]} session
        user-details (ds/find-by-key googleuser-key)]
    (if user-details
      (handler (assoc-in req [:user-details] user-details))
      (if (= (:uri req) "/api")
        (-> (response/response "Authorisation required")
            (ring.util.response/status 401))
        (do
          (ds/save (util/safe-merge session {:last-uri (:uri req)}))
          (redirect "/login" :temporary-redirect))))))



(defn auth-user [handler]
  "Add :user-details based on the :session :identity and call the handler,
  or if the user details can't be found, redirect to /login. Don't apply the test to any of the auth-routes"
  (fn [req]
    (let [session (ds/find-by-key (get-in req [:session :identity]))]
      (cond
        (re-matches #"^/(login|authorise|oauth2callback|login-failed)" (:uri req)) (handler req)
        (nil? session) (no-session req)
        :else (check-user handler req session)))))

(defn add-auth [app-routes error-routes extra]
  (->
   (compojure.core/routes app-routes auth-routes error-routes)
   auth-user
   ;no-cache
   (wrap-session {
                  :store (cookie-store {:key "***REMOVED*** "})
                  :cookie-name "S"
                  :cookie-attrs (merge {:max-age (* 3600 24 2) :http-only true} extra)})))

