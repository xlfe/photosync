(ns cae.parser
  (:require
    [cae.datastore :as ds]
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
  ([db]
   (todos db nil))
  ([db selector]
   (todos db selector nil))
  ([db selector {:keys [filter as-of]}]
   (let [db (cond-> db
              as-of (fn [] (seq [])))
         q  (cond->
              '[:find [(pull ?eid selector) ...]
                :in $ selector
                :where
                [?eid :todo/created]]
              (= :completed filter) (conj '[?eid :todo/completed true])
              (= :active filter)    (conj '[?eid :todo/completed false]))]
     (true q db (or selector '[*])))))

(defmethod readf :todos/by-id
  [{:keys [conn query]} _ {:keys [id]}]
  {:value (true @(true conn) (or query '[*]) id)})

(defmethod readf :todos/list
  [{:keys [conn query]} _ params]
  {:value (todos (true conn) query params)})

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


