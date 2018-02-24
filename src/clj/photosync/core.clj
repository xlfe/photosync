(ns photosync.core
  (:require [compojure.core :refer [defroutes ANY GET POST]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.ssl :refer [wrap-hsts]]
            [ring.util.response :refer [redirect]]
            [clojure.walk :as walk]
            [photosync.datastore :as ds]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.util.response :refer [resource-response]]
            [clojure.tools.logging :as log]
            [photosync.util :as util]
            [liberator.core :refer [resource defresource]]
            [clojure.edn :as edn]
            [photosync.parser :as parser]
            [om.next.server :as om]
            [photosync.model :as model]
            [photosync.users :as users]
            [photosync.secrets :as secrets]
            [photosync.auth :as pauth]


            [cheshire.core :as json]
            [buddy.sign.jwt :as jwt]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer (wrap-authentication)]

            [compojure.route :as route]))

;
; Authentication
;


;(defn unauthorized-handler
;  [request metadata]
;  (-> (response "Unauthorized APP request")
;      (assoc :status 403))
;
(def auth-backend (backends/jws
                    {:secret secrets/jws-secret
                     ;:unauthorized-handler unauthorized-handler
                     :options {:alg :hs512}}))


;(defn generate-auth-token
;  [request]
;  (let [data (:form-params request)
;        user (find-user (:username data)   ;; (implementation ommited)
;                        (:password data)})
;        token (jwt/sign {:user (:id user)} secret)
;    {:status 200
;     :body (json/encode {:token token})
;     :headers {:content-type "application/json"})



;
;
; Other stuff
;


(def edn-api-defaults
  {
   :available-media-types ["application/edn"]})

(def edn-api-loggedin
  (merge edn-api-defaults
         {:authorized? authenticated?}))

(defn read-inputstream-edn [input]
  (edn/read
    {:eof nil}
    (java.io.PushbackReader.
      (java.io.InputStreamReader. input "UTF-8"))))

(defn parse-edn-body [handler]
  (fn [request]
    (handler (if-let [body (:body request)]
               (assoc request
                 :edn-body (read-inputstream-edn body))
               request))))



;(defresource
;  classes
;  edn-api-defaults
;  :allowed-methods [:get :post]
;  :post! (fn [ctx]
;           (dosync
;             (let [data (get-in ctx [:request :edn-body])]
;               (println data)
;               {::id 1})))
;  :post-redirect? (fn [ctx] {:location (format "/postbox/%s" (::id ctx))})
;  :handle-ok (pr-str (ds/query Classes)))




;(defresource
;  init
;  edn-api-defaults
;  :allowed-methods [:get]
;  :handle-ok (pr-str
;               {:classes {:url "/classes" :coll (map fake-datomic (ds/query Classes))}}))))

(defn api [req]
  (let [data ((om/parser {:read parser/readf :mutate parser/mutatef}) {} (get-in req [:request :edn-body]))]
    {::data data}))


(defn check-user [req]
  (not (= nil (:user-info req))))


(defresource
  api-resource
  edn-api-loggedin
  :authorized? check-user
  :allowed-methods [:get :post]
  :post! api
  :new? false
  :respond-with-entity? true
  :handle-ok (fn [ctx]
                 (prn-str (::data ctx))))
                 ;(prn-str (merge {:compassus.core/route :index} (::data ctx)))))

(defn login-or-redirect
  [destination-uri]
  (fn [request]
    (let [{:keys [user user-service]} (users/user-info request)]
      (log/info (str "USER: " (prn-str user)))
      (if (.isUserLoggedIn user-service)
        {:status 302 :headers {"Location" destination-uri}}
        {:status 302 :headers {"Location" (.createLoginURL user-service destination-uri)}}))))



(defroutes app

           (GET "/oauth2callback" {params :query-params}
             (pauth/google params))
           (GET "/gauth" [] (redirect pauth/red))
           (GET "/login" [] (login-or-redirect "/"))
           ;(ANY "/classes" [] classes)
           (ANY "/api" [] api-resource)
           (GET  "/" [] (resource-response "index.html" {:root "public/html"}))
           (route/resources "/")
           (route/not-found (resource-response "404.html" {:root "public/html"})))

(def prod-handler
  (-> app
      wrap-params
      wrap-hsts
      ;(wrap-authentication auth-backend)
      users/wrap-with-user-info
      parse-edn-body))

(def reload-handler
  (-> app
      wrap-params
      ;(wrap-authentication auth-backend)
      users/wrap-with-user-info
      parse-edn-body
      wrap-reload))
