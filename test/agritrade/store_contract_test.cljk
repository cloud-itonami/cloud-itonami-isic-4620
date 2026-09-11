(ns agritrade.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [agritrade.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/agri-order s "ao-1"))))
      (is (= "Akita Grain Traders Co" (:counterparty (store/agri-order s "ao-1"))))
      (is (= :plant (:consignment-kind (store/agri-order s "ao-1"))))
      (is (= "Feed Wheat No. 2 (bulk)" (:commodity (store/agri-order s "ao-1"))))
      (is (= "ATL" (:jurisdiction (store/agri-order s "ao-2"))))
      (is (false? (:credit-cleared? (store/agri-order s "ao-3"))) "ao-3 credit not cleared")
      (is (nil? (:contract-terms (store/agri-order s "ao-4"))) "ao-4 no contract-terms")
      (is (false? (:sanctions-screened? (store/agri-order s "ao-5"))) "ao-5 sanctions not screened")
      (is (false? (:phytosanitary-certificate? (store/agri-order s "ao-6"))) "ao-6 no phytosanitary certificate")
      (is (= :animal (:consignment-kind (store/agri-order s "ao-7"))) "ao-7 is a livestock consignment")
      (is (= "head" (:unit (store/agri-order s "ao-7"))))
      (is (false? (:animal-health-certificate? (store/agri-order s "ao-8"))) "ao-8 no animal-health certificate")
      (is (false? (:dispatched? (store/agri-order s "ao-1"))))
      (is (false? (:invoiced? (store/agri-order s "ao-1"))))
      (is (= ["ao-1" "ao-2" "ao-3" "ao-4" "ao-5" "ao-6" "ao-7" "ao-8"]
             (mapv :id (store/all-agri-orders s))))
      (is (nil? (store/assessment-of s "ao-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/delivery-history s)))
      (is (= [] (store/invoice-history s)))
      (is (zero? (store/next-delivery-sequence s "JPN")))
      (is (zero? (store/next-invoice-sequence s "JPN")))
      (is (false? (store/agri-order-already-dispatched? s "ao-1")))
      (is (false? (store/agri-order-already-invoiced? s "ao-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :order/upsert
                                 :value {:id "ao-1" :counterparty "Akita Grain Traders Co"}})
        (is (= "Akita Grain Traders Co" (:counterparty (store/agri-order s "ao-1"))))
        (is (= "JPN" (:jurisdiction (store/agri-order s "ao-1"))) "unrelated field preserved"))
      (testing "biosecurity-assessment payloads commit and read back"
        (store/commit-record! s {:effect :biosecurity-assessment/set :path ["ao-1"]
                                 :payload {:jurisdiction "JPN" :consignment-kind :plant :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :consignment-kind :plant :checklist ["a" "b"]} (store/assessment-of s "ao-1"))))
      (testing "delivery drafts a record and advances the delivery sequence"
        (store/commit-record! s {:effect :order/mark-dispatched :path ["ao-1"]})
        (is (= "JPN-DELIVERY-000000" (get (first (store/delivery-history s)) "record_id")))
        (is (= "agri-delivery-draft" (get (first (store/delivery-history s)) "kind")))
        (is (true? (:dispatched? (store/agri-order s "ao-1"))))
        (is (= 1 (count (store/delivery-history s))))
        (is (= 1 (store/next-delivery-sequence s "JPN")))
        (is (true? (store/agri-order-already-dispatched? s "ao-1"))))
      (testing "invoice settlement drafts a record and advances the invoice sequence"
        (store/commit-record! s {:effect :order/mark-invoiced :path ["ao-1"]})
        (is (= "JPN-INVOICE-000000" (get (first (store/invoice-history s)) "record_id")))
        (is (= "agri-invoice-draft" (get (first (store/invoice-history s)) "kind")))
        (is (true? (:invoiced? (store/agri-order s "ao-1"))))
        (is (= 1 (count (store/invoice-history s))))
        (is (= 1 (store/next-invoice-sequence s "JPN")))
        (is (true? (store/agri-order-already-invoiced? s "ao-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/agri-order s "nope")))
    (is (= [] (store/all-agri-orders s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/delivery-history s)))
    (is (= [] (store/invoice-history s)))
    (is (zero? (store/next-delivery-sequence s "JPN")))
    (is (zero? (store/next-invoice-sequence s "JPN")))
    (store/with-agri-orders s {"x" {:id "x" :order-id "AO-X" :consignment-kind :plant
                                    :commodity "Feed Wheat No. 2 (bulk)" :quantity 5000 :unit "metric tons"
                                    :counterparty "c" :price 245.00
                                    :contract-terms "FOB elevator, net 30 days"
                                    :credit-cleared? true :sanctions-screened? true
                                    :phytosanitary-certificate? true :animal-health-certificate? true
                                    :dispatched? false :invoiced? false
                                    :jurisdiction "JPN" :status :intake
                                    :dispatch-number nil :invoice-number nil}})
    (is (= "c" (:counterparty (store/agri-order s "x"))))))
