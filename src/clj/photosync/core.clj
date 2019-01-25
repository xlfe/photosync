(ns photosync.core
  (:require
    [compojure.core :refer [defroutes routes ANY GET POST wrap-routes]]
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
    [photosync.smugmug :as smugmug]
    [photosync.secrets :as secrets]
    [photosync.auth :as auth]
    [photosync.cron :as cron]
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
  user-api-resource
  :available-media-types ["application/edn"]
  :authorized? check-session
  :allowed-methods [:get :post]
  :post! api
  :new? false
  :respond-with-entity? true
  :handle-ok (fn [ctx]
                 (prn-str (::data ctx))))

(def resource-root {:root "public"})

(defroutes user-routes
     (ANY "/api" [] user-api-resource)
     (GET  "/" [] (resource-response "html/index.html" resource-root))
     (route/resources "/" resource-root))

(defroutes error-routes
   (route/not-found (resource-response "html/404.html" resource-root)))

(def core-handler
  (-> (routes
        auth/auth-routes
        (-> user-routes
            (wrap-routes auth/auth-user))
        error-routes)
      auth/cookie-wrap
      wrap-params       ; parse urlencoded parameters from the query string and form body
      parse-edn-body))

(def prod-handler
  (-> core-handler
      wrap-reload
      wrap-hsts))         ; HTTP Strict Transport Security

(def dev-handler
  (-> (routes
        cron/cron-routes
        smugmug/smug-routes
        core-handler)
      wrap-reload))


