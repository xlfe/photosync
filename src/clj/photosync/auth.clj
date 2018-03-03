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
            [hyperion.api :as ds]))

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
(def REDIRECT_URI "http://localhost:8080/oauth2callback")
(def google-user (atom {:google-id "" :google-name "" :google-email ""}))

(def SCOPES "https://picasaweb.google.com/data/ openid email profile")
;(def SCOPES "openid email profile")

;https://developers.google.com/picasa-web/docs/3.0/developers_guide_protocol
;https://developers.google.com/identity/protocols/OpenIDConnect#server-flow
;https://developers.google.com/identity/protocols/CrossClientAuth

(def red (str "https://accounts.google.com/o/oauth2/auth?"
              "scope=" SCOPES "&"
              "redirect_uri=" (ring.util.codec/url-encode REDIRECT_URI) "&"
              "response_type=code&"
              "client_id=" (ring.util.codec/url-encode CLIENT_ID) "&"
              ;"prompt=none&"
              "access_type=offline"))

(defn google-callback [req]
  (let [params (:params (ring.middleware.params/params-request req))
        access-token-response (client/post "https://accounts.google.com/o/oauth2/token"
                                           {:form-params {:code (get params "code")
                                                          :client_id CLIENT_ID
                                                          :client_secret CLIENT_SECRET
                                                          :redirect_uri REDIRECT_URI
                                                          :grant_type "authorization_code"}})
        user-details (parse/parse-string (:body (client/get (str "https://www.googleapis.com/oauth2/v1/userinfo?access_token="
                                                                 (get (parse/parse-string (:body access-token-response)) "access_token")))) true)]
    (log/info access-token-response)
    (log/info user-details)
    (assoc-in (redirect "/") [:session :identity] (:email user-details))))

(def test-user
  {:given_name "Felix", :email "felixb@gmail.com",
   :locale "en-GB", :name "Felix Barbalet",
   :family_name "Barbalet",
   :link "https://plus.google.com/+FelixBarbalet",
   :id "108070438918136791491",
   :picture "https://lh5.googleusercontent.com/-aQqoVIBsTYQ/AAAAAAAAAAI/AAAAAAAAI1o/AnsRewIufl8/photo.jpg",
   :verified_email true, :gender "male"})



(defn set-user [req]
  (println (ds/save {:kind :login} test-user))
  (println (count (ds/find-by-kind :login)))
  (println (ds/find-records-by-kind :login))
  (assoc-in
    (response (str (:identity (:session req))))
    [:session :identity] test-user));{:_id 1, :username "jshiosdhifsodhiouho", :role :admin}))


(defroutes auth-routes
 (GET "/oauth2callback" [] google-callback)
 (GET "/gauth" [] (redirect red))
 (GET "/testauth" [] set-user))

(defn add-auth [app]
  (-> (compojure.core/routes auth-routes app)
   (wrap-session {
                  :store (cookie-store "1234567887654321")
                  :cookie-name "S"
                  :cookie-attrs {:max-age 3600}})

   (wrap-authentication backend)))


