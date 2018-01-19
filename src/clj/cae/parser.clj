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
  (println query _)
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
  ;[env key params]
  [env key {:keys [db/id todo/completed todo/title] :as params}]
  {:value {:keys [[:todos/by-id id]]}
   :action
   (fn []
     (println completed)
     (if-let [entity (ds/retrieve Todo id)]
       (let [updated (merge entity
                        (when (not= nil completed)
                         {:completed completed})
                        (when (not= nil title)
                         {:title title}))]
        (println entity)
        (println updated)
        (ds/fake-datomic (ds/save! updated)))
      (str "error")))})


(defmethod mutatef 'todo/delete
  [{:keys [conn]} k {:keys [db/id]}]
  {:value {:keys [:todos/list]}
   :action
   (fn []
     @(true conn [[:db.fn/retractEntity id]]))})


