(ns cae.core
  (:require [compojure.core :refer [defroutes ANY GET POST]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.util.response :refer [resource-response]]
            [clojure.tools.logging :as log]
            [liberator.core :refer [resource defresource]]
            [clojure.edn :as edn]
            [cae.model]
            [net.xlfe/datastore :as d]
            [cae.datastore :as ds]
            [compojure.route :as route])
  (:import
    [cae.model mclass]))

(def edn-api-defaults
  {
   :available-media-types ["application/edn"]})

(def edn-api-loggedin
  (merge edn-api-defaults
         {:authorized? false}))

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




(defresource
  classes
  edn-api-defaults
  :allowed-methods [:get :post]
  :post! (fn [ctx]
           (dosync
             (let [data (:edn-body ctx)
                   id        (str (java.util.UUID/randomUUID))
                   e (cae.model/make-mclass {
                                             ;todo: use datastore generated IDs
                                             :class/id id
                                             :class/title "test"
                                             :done false})]
               (ds/save! e)

               {::id 1})))
  :post-redirect? (fn [ctx] {:location (format "/postbox/%s" (::id ctx))})
  :handle-ok (pr-str (ds/query mclass)))


(defresource
  init
  edn-api-defaults
  :allowed-methods [:get]
  :handle-ok (pr-str
               {:classes {:url "/classes" :coll (->> (ds/query mclass)
                                                     (map #(into {} %))
                                                     vec)}}))


(defroutes app
           (ANY "/init" [] init)
           (ANY "/classes" [] classes)
           (route/resources "/app/")
           (GET  "/app/" [] (resource-response "index.html" {:root "public/html"}))
           (route/not-found (resource-response "404.html" {:root "public/html"})))

(def prod-handler
  (-> app
      wrap-params
      parse-edn-body))

(def reload-handler
  (-> prod-handler
      wrap-reload))
