(ns cae.parser
  (:require
    [cae.datastore :as ds]
    [clojure.tools.logging :as log]
    [cae.model :as model])
  (:refer-clojure :exclude [read])
  (:import
    [cae.model Todo]))

;; =============================================================================
;; Reads

(defmulti readf (fn [env k params] k))

(defmethod readf :default
  [_ k _]
  {:value {:error (str "No handler for read key " k)}})

(defn todos
  ([]
   (into [] (map ds/fake-datomic (ds/query Todo))))
  ([selector]
   (ds/query
     Todo
     :filter [:= :id selector])))

(defmethod readf :todos/by-id
  [{:keys [query]} _ {:keys [id]}]
  ;{:value (ds/retrieve Todo id)}
  {:value (todos)})

(defmethod readf :todos/list
  [{:keys [query]} _ params]
  {:value (todos)})

;; =============================================================================
;; Mutations

(defmulti mutatef (fn [env k params] k))

(defmethod mutatef :default
  [_ k _]
  {:value {:error (str "No handler for mutation key " k)}})

(defmethod mutatef 'todos/create
  [{:keys [conn]} k {:keys [:todo/title]}]
  {:value {:keys [:todos/list]}
   :action
   (fn []
     @(true conn
        [{:db/id          0
          :todo/title     title
          :todo/completed false
          :todo/created   (java.util.Date.)}]))})

(defmethod mutatef 'todo/update
  [{:keys [conn]} k {:keys [db/id todo/completed todo/title]}]
  {:value {:keys [[:todos/by-id id]]}
   :action
   (fn []
     @(true conn
        [(merge {:db/id id}
           (when (or (true? completed) (false? completed))
             {:todo/completed completed})
           (when title
             {:todo/title title}))]))})

(defmethod mutatef 'todo/delete
  [{:keys [conn]} k {:keys [db/id]}]
  {:value {:keys [:todos/list]}
   :action
   (fn []
     @(true conn [[:db.fn/retractEntity id]]))})


