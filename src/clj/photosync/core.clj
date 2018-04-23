(ns photosync.core
  (:require
    [compojure.core :refer [defroutes ANY GET POST]]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.ssl :refer [wrap-hsts]]
    [clojure.walk :as walk]
    [ring.middleware.reload :refer [wrap-reload]]
    [ring.util.response :refer [redirect resource-response]]
    [clojure.tools.logging :as log]
    [photosync.util :as util]
    [liberator.core :refer [resource defresource]]
    [clojure.edn :as edn]
    [photosync.parser :as parser]
    [om.next.server :as om]
    [photosync.model :as model]
    [photosync.secrets :as secrets]
    [photosync.auth :as auth]
    [hyperion.gae]
    [hyperion.api :as ds]

    [cheshire.core :as json]
    [compojure.route :as route]))


(ds/set-ds! (hyperion.gae/new-gae-datastore))

;(defn unauthorized-handler
;  [request metadata]
;  (-> (response "Unauthorized APP request")
;      (assoc :status 403))
;

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
         {:authorized? true}))
         ;{:authorized? authenticated?}))

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
  (let [user (get-in req [:request :user-details])
        data ((om/parser {:read parser/readf :mutate parser/mutatef}) {:user-details user} (get-in req [:request :edn-body]))]
    {::data data}))


(defn check-session [req]
  ;(println (str "Check Session: " (get-in req [:request :user-details])))
  (not (= nil (get-in req [:request :user-details]))))


(defresource
  api-resource
  edn-api-loggedin
  :authorized? check-session
  :allowed-methods [:get :post]
  :post! api
  :new? false
  :respond-with-entity? true
  :handle-ok (fn [ctx]
                 (prn-str (::data ctx))))
                 ;(prn-str (merge {:compassus.core/route :index} (::data ctx)))))


(defroutes app
   (ANY "/api" [] api-resource)
   (GET  "/" [] (resource-response "index.html" {:root "public/html"}))
   (route/resources "/")
   (route/not-found (resource-response "404.html" {:root "public/html"})))

(def prod-handler
  (-> app               ; main app routes
      wrap-hsts         ; HTTP Strict Transport Security
      wrap-params       ; parse urlencoded parameters from the query string and form body
      (auth/add-auth {:secure true})     ; authentication endpoints, adds :session to request based on cookies
      parse-edn-body))

(defn test-db [req]
  {:body (str
           (ds/save (model/google-user) :email "test@test.com" :locale "AU")
           (ds/save (model/user-session) :google-id "test@test.com"))})


(defroutes dev-routes
  (GET "/test-db" [] test-db))


(def dev-handler
  (->
    (compojure.core/routes dev-routes app)
    wrap-params
    auth/debug-user-auth
    (auth/add-auth {:secure false})     ; authentication endpoints, adds :session to request based on cookies
    wrap-reload ; add hot reload
    parse-edn-body))
