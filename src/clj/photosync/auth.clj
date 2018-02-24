(ns photosync.auth
  (:use compojure.core)
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [cheshire.core :as parse]))

(def login-uri "https://accounts.google.com")
(def CLIENT_ID "***REMOVED***.apps.googleusercontent.com")
(def CLIENT_SECRET "***REMOVED***")
(def REDIRECT_URI "http://localhost:8080/oauth2callback")
(def google-user (atom {:google-id "" :google-name "" :google-email ""}))

(def SCOPES "https://picasaweb.google.com/data/ openid email profile")

;https://developers.google.com/picasa-web/docs/3.0/developers_guide_protocol
;https://developers.google.com/identity/protocols/OpenIDConnect#server-flow
;https://developers.google.com/identity/protocols/CrossClientAuth

(def red (str "https://accounts.google.com/o/oauth2/auth?"
              "scope=" SCOPES "&"
              "redirect_uri=" (ring.util.codec/url-encode REDIRECT_URI) "&"
              "response_type=code&"
              "client_id=" (ring.util.codec/url-encode CLIENT_ID) "&"
              "prompt=consent&"
              "access_type=offline"))

(defn google [params]
  (let [access-token-response (client/post "https://accounts.google.com/o/oauth2/token"
                                           {:form-params {:code (get params "code")
                                                          :client_id CLIENT_ID
                                                          :client_secret CLIENT_SECRET
                                                          :redirect_uri REDIRECT_URI
                                                          :grant_type "authorization_code"}})
        user-details (parse/parse-string (:body (client/get (str "https://www.googleapis.com/oauth2/v1/userinfo?access_token="
                                                                 (get (parse/parse-string (:body access-token-response)) "access_token")))))]
    (log/info access-token-response)
    (log/info user-details)
    (swap! google-user #(assoc % :google-id %2 :google-name %3 :google-email %4) (get user-details "id") (get user-details "name") (get user-details "email"))))

