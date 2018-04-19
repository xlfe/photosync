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

            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer (wrap-authentication)]
            [buddy.sign.jwe :as jwe]
            [buddy.core.hash :as hash]
            [buddy.core.keys :as keys]
            [cheshire.core :as parse]
            [hyperion.api :as ds]
            [photosync.model :as models]))

  ;(:import [java.security Security]))


; Required to be able to use AES256
;(Security/setProperty "crypto.policy" "unlimited")
;(def pubkey (keys/public-key (-> "keys/pubkey.pem" io/resource io/file)))
;(def privkey (keys/private-key (-> "keys/privkey.pem" io/resource io/file) pem-secret))


(def jwe-secret (hash/sha256 "p51l4Gh5r6UB81ugvzjjTIhpA0NGKfFqPJAxx&Gj(:X\\M}iR3?3bAB!a^"))
(def backend (backends/jwe {:secret jwe-secret}))




(def login-uri "https://accounts.google.com")
(def CLIENT_ID "***REMOVED***.apps.googleusercontent.com")
(def CLIENT_SECRET "***REMOVED***")
;(def REDIRECT_URI "http://localhost:8080/oauth2callback")
(def REDIRECT_URI "https://photosync.net/oauth2callback")
(def SCOPES "https://picasaweb.google.com/data/ openid email profile")
;(def SCOPES "openid email profile")

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


(defn save-session [req details]
  (let [key (:key (ds/save (models/user-session details)))]
    (assoc-in
      (response (str (:identity (:session req))))
      [:session :identity] key)))


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
                              access-token)))))

(defn google-complete-flow
   [code]
   (let [access-token-response (google-get-token {:code code :grant_type "authorization_code"})
         access-token (get access-token-response "access_token")
         refresh-token (get access-token-response "refresh_token")
         user-details (google-user-details access-token)]
    (log/info access-token-response)
    (log/info access-token)
    (log/info user-details)
    (log/info refresh-token)
    (assoc-in (redirect "/") [:session :identity] (:email user-details))))


; If user doesn't consent, redirect to a message
; If user does consent, complete the flow
(defn google-callback [req]
  (if-let [code (get (:params (ring.middleware.params/params-request req)) "code")]
    (google-complete-flow code)
    (redirect "/login-failed")))






(defroutes auth-routes
 ;Simply login to the app
 (GET "/login" [] (redirect (gauth-redirect false)))
 (GET "/authorise" [] (redirect (gauth-redirect true)))
 (GET "/oauth2callback" [] google-callback))

(defn add-auth [app]
  (-> (compojure.core/routes auth-routes app)
   (wrap-session {
                  :store (cookie-store "Biy}+y{JolJK%1/)F")
                  :cookie-name "S"
                  :cookie-attrs {:max-age 3600}})

   (wrap-authentication backend)))



(defn debug-user-auth [handler]
  (fn [request]
    ;(handler (assoc-in request [:session] nil))))
    (handler (assoc-in request [:session :identity] "test-user@gmail.com"))))

