(ns cae.core
  (:require [compojure.core :refer [defroutes ANY GET POST]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.walk :as walk]
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




(defresource
  classes
  edn-api-defaults
  :allowed-methods [:get :post]
  :post! (fn [ctx]
           (dosync
             (let [data (get-in ctx [:request :edn-body])]
               (println data)
               {::id 1})))
  :post-redirect? (fn [ctx] {:location (format "/postbox/%s" (::id ctx))})
  :handle-ok (pr-str (ds/query Classes)))


(defn map->nsmap
  [m n]
  (reduce-kv (fn [acc k v]
               (let [new-kw
                              (keyword (str n) (name k))]
                 (assoc acc new-kw v)))
             {} m))

(defn fake-datomic
  "add a namespace to keys to look like a datom"
  [e]
  (let [kind
        (->> (util/->kebab-case (ds/entity-kind e)))]
    (map->nsmap e kind)))



(defresource
  init
  edn-api-defaults
  :allowed-methods [:get]
  :handle-ok (pr-str
               {:classes {:url "/classes" :coll (map fake-datomic (ds/query Classes))}}))



(defn api [req]
  (let [data ((om/parser {:read parser/readf :mutate parser/mutatef})
               {:conn (:datomic-connection req)} (:transit-params req))
        data' (walk/postwalk (fn [x]
                               (if (and (sequential? x) (= :result (first x)))
                                 [(first x) (dissoc (second x) :db-before :db-after :tx-data)]
                                 x))
                             data)]
    (pr-str data')))




(defroutes app
           (ANY "/init" [] init)
           (ANY "/classes" [] classes)
           (ANY "/jobs" [:as req]
                 (fn [req]
                   (let [job (model/make-classes
                               {:id        (str (java.util.UUID/randomUUID))
                                :title "testing one two three"
                                :status    "complete"})] ;; FIXME "created"
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
