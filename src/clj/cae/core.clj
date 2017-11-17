(ns cae.core
  (:require [compojure.core :refer [defroutes ANY GET]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.util.response :refer [resource-response]]
            [clojure.tools.logging :as log]
            [liberator.core :refer [resource defresource]]
            [clojure.edn :as edn]
            [compojure.route :as route])
  (:import [com.google.appengine.api.datastore DatastoreServiceFactory, Query, KeyRange, Entity]
           [com.google.appengine.api.datastore FetchOptions$Builder]))



(defn read-db
  []
  (let [datastore (DatastoreServiceFactory/getDatastoreService)
        query (Query. "item")
        prepared-query (.prepare datastore query)
        result (.asList prepared-query (FetchOptions$Builder/withDefaults))]
    (pr-str (map #(.getProperty %1 "name")
                 result))))

(defn write-db
  [name]
  (let [datastore (DatastoreServiceFactory/getDatastoreService)
        entity (Entity. "item")]
    (.setProperty entity "name" name)
    (let [key (.put datastore entity)]
      (str key))))


(defn txn
  []
  (let [ds (DatastoreServiceFactory/getDatastoreService)
        keys (KeyRange. nil "ident" 1 1)
        entity (Entity. "item")]

    (.setProperty entity "id" (.getStart keys))

    (let [key (.put ds entity)]
      (str key))))



(defn get-classes [db]
  (vec [{:id "test" :title "test" :eid "eid"}]))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})


(defn init []
  (generate-response
    {:classes {:url "/classes" :coll []}}))
;(d/db conn))}}))

(defn update-class [params]
  (let [db    nil ;(d/db conn)
        id    (:class/id params)
        title (:class/title params)
        eid   (ffirst
                nil)]
    ;(d/q '[:find ?class
    ;       :in $ ?id
    ;       :where
    ;       [?class :class/id ?id]
    ;  db id)]
    ;(d/transact conn [[:db/add eid :class/title title]])
    (generate-response {:status :ok})))

(defn classes []
  nil)
;(generate-response (get-classes (d/db conn))))

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

(defn
  wrap-dir-index [handler]
  (fn [req]
    (do
      (log/info req)
      (handler (update-in req [:uri] #(if (= "/app/" %) "/app/index.html" %))))))







(defn tiger-not-found
  [ctx]
  (format "That is not THE right word: '%s'. Try again?" (get-in ctx [:request :params "word"])))

;(defroutes routes
           ;(GET "/" [] (index))
           ;(GET "/classes" [] (classes))
           ;(POST "/classes" {params :edn-body} (create-class params))
           ;(PUT "/classes" {params :edn-body} (update-class params))
           ;(route/files "/" {:root "resources/public"})))))


(defroutes app
           (GET "/init" [] (init))
           (ANY "/details" [] (resource
                                :available-media-types ["text/html"]
                                :handle-ok
                                           (fn [_]
                                              (str "<h1>Hello World</h1><h2>db contains:</h2><p>" (read-db) "</p>"))))

           (GET "/new/:name" [name] (write-db name))
           (GET "/txn" [] (txn))
           (ANY "/secret" [] (resource :available-media-types ["text/html"])
                       :exists? (fn [ctx]
                                  (= "tiger" (get-in ctx [:request :params "word"])))
                       :handle-ok "You found the secret word!"
                       :handle-not-found tiger-not-found)

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
