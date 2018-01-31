(ns cae.core
  (:require [compojure.core :refer [defroutes ANY GET POST]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.walk :as walk]
            [cae.datastore :as ds]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.util.response :refer [resource-response]]
            [clojure.tools.logging :as log]
            [cae.util :as util]
            [liberator.core :refer [resource defresource]]
            [clojure.edn :as edn]
            [cae.parser :as parser]
            [om.next.server :as om]
            [cae.model :as model]
            [compojure.route :as route]))

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



;
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
  (let [data ((om/parser {:read parser/readf :mutate parser/mutatef})
              {} (:edn-body req))
        data' (walk/postwalk (fn [x]
                               (if (and (sequential? x) (= :result (first x)))
                                 [(first x) (dissoc (second x) :db-before :db-after :tx-data)]
                                 x))
                             data)]
    (pr-str data')))




(defroutes app
           ;(ANY "/init" [] init)
           ;(ANY "/classes" [] classes)
           (ANY "/api" [] api)
           (ANY "/jobs" [:as req]
                 (fn [req]
                   (let [job (model/make-todo
                               {:id        (rand-int 10000000)
                                :title "testing one two three"
                                :created   (java.util.Date.)
                                :completed    false})] ;; FIXME "created"
                     (prn :created-job job)
                     (ds/save! job) 200 "test")))
           (GET  "/app/" [] (resource-response "index.html" {:root "public/html"}))
           (route/resources "/app/")
           (route/not-found (resource-response "404.html" {:root "public/html"})))

(def prod-handler
  (-> app
      wrap-params
      parse-edn-body))

(def reload-handler
  (-> prod-handler
      wrap-reload))
