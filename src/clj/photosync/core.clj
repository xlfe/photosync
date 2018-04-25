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

;
; Other stuff
;


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

(defn api [req]
  (let [user (get-in req [:request :user-details])
        data ((om/parser {:read parser/readf :mutate parser/mutatef}) {:user-details user} (get-in req [:request :edn-body]))]
    {::data data}))

(defn check-session [req]
  (not (= nil (get-in req [:request :user-details]))))

(defresource
  api-resource
  :available-media-types ["application/edn"]
  :authorized? check-session
  :allowed-methods [:get :post]
  :post! api
  :new? false
  :respond-with-entity? true
  :handle-ok (fn [ctx]
                 (prn-str (::data ctx))))
                 ;(prn-str (merge {:compassus.core/route :index} (::data ctx)))))

(def resource-root {:root "public"})

(defroutes app-routes
   (ANY "/api" [] api-resource)
   (GET  "/" [] (resource-response "html/index.html" resource-root))
   (route/resources "/" resource-root))

(defroutes error-routes
   (route/not-found (resource-response "html/404.html" resource-root)))

(def prod-handler
  (-> app-routes ; main app routes
      wrap-hsts         ; HTTP Strict Transport Security
      wrap-params       ; parse urlencoded parameters from the query string and form body
      parse-edn-body
      (auth/add-auth error-routes {:secure true})))     ; authentication using cookies and google user details

(def dev-handler
  (-> app-routes
      wrap-params
      parse-edn-body
      (auth/add-auth error-routes {:secure false})     ; authentication using cookies and google user details
      wrap-reload)) ; add hot reload
