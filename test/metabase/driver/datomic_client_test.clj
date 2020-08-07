(ns metabase.driver.datomic-client-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [matcher-combinators.matchers :as m]
            [metabase.driver.datomic-client :refer :all]
            [gini.datomic-harness :as harness]))

(defn db-of
  "Returns an empty dev-local DB, but with the current application schema.
   The returned DB can be used in `dc/with`."
  [& txs]
  (reduce harness/conj-tx (harness/test-db) txs))

(deftest can-connect?-test
  (let [_ (harness/test-db)]
    (is (can-connect? harness/test-db-spec))))

(deftest derive-table-names-test
  (testing "infer 1 table from schema"
    (is (match?
         (m/in-any-order ["table"])
         (derive-table-names
          (db-of [{:db/ident :table/a1
                   :db/valueType :db.type/ref
                   :db/cardinality :db.cardinality/one}
                  {:db/ident :table/a2
                   :db/valueType :db.type/ref
                   :db/cardinality :db.cardinality/one}])))))
  (testing "infer 2 table from schema"
    (is (match?
         (m/in-any-order ["table-1", "table-2"])
         (derive-table-names
          (db-of [{:db/ident :table-1/a
                   :db/valueType :db.type/ref
                   :db/cardinality :db.cardinality/one}
                  {:db/ident :table-1/b
                   :db/valueType :db.type/ref
                   :db/cardinality :db.cardinality/one}
                  {:db/ident :table-2/a
                   :db/valueType :db.type/ref
                   :db/cardinality :db.cardinality/one}]))))))

(deftest describe-table-test
  (testing "infer attributes with type from given table"
    (is (match?
         (m/in-any-order [[:table-1/a :db.type/ref]
                          [:table-1/b :db.type/long]])
         (table-columns
          (db-of [{:db/ident :table-1/a
                   :db/valueType :db.type/ref
                   :db/cardinality :db.cardinality/one}
                  {:db/ident :table-1/b
                   :db/valueType :db.type/long
                   :db/cardinality :db.cardinality/one}
                  {:db/ident :table-2/a
                   :db/valueType :db.type/string
                   :db/cardinality :db.cardinality/one}])
          "table-1")))))
