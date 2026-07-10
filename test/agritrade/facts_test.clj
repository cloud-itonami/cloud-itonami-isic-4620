(ns agritrade.facts-test
  (:require [clojure.test :refer [deftest is]]
            [agritrade.facts :as facts]))

(deftest jpn-has-a-spec-basis-for-both-kinds
  (is (some? (facts/spec-basis "JPN" :plant)))
  (is (some? (facts/spec-basis "JPN" :animal)))
  (is (string? (:provenance (facts/spec-basis "JPN" :plant))))
  (is (string? (:provenance (facts/spec-basis "JPN" :animal)))))

(deftest plant-and-animal-spec-basis-are-genuinely-different-entries
  (is (not= (:legal-basis (facts/spec-basis "JPN" :plant))
            (:legal-basis (facts/spec-basis "JPN" :animal)))
      "phytosanitary law and animal-health law are different statutes even in the same jurisdiction")
  (is (not= (facts/spec-basis "USA" :plant) (facts/spec-basis "USA" :animal))))

(deftest all-four-seeded-jurisdictions-have-required-evidence-for-both-kinds
  ;; every seeded agri-wholesale jurisdiction actually has a real
  ;; required-evidence set reported honestly here, for BOTH kinds
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU"]
          kind [:plant :animal]]
    (is (seq (facts/evidence-checklist iso3 kind)) (str iso3 "/" kind " required-evidence"))))

(deftest plant-checklist-cites-phytosanitary-not-animal-certificate
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU"]]
    (is (some #(re-find #"(?i)phytosanitary" %) (facts/evidence-checklist iso3 :plant))
        (str iso3 " :plant checklist should require a phytosanitary certificate"))
    (is (not-any? #(re-find #"(?i)animal health" %) (facts/evidence-checklist iso3 :plant))
        (str iso3 " :plant checklist should not require an animal-health certificate"))))

(deftest animal-checklist-cites-animal-health-not-phytosanitary-certificate
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU"]]
    (is (some #(re-find #"(?i)animal health" %) (facts/evidence-checklist iso3 :animal))
        (str iso3 " :animal checklist should require an animal-health certificate"))
    (is (not-any? #(re-find #"(?i)phytosanitary" %) (facts/evidence-checklist iso3 :animal))
        (str iso3 " :animal checklist should not require a phytosanitary certificate"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL" :plant)))
  (is (nil? (facts/spec-basis "ATL" :animal))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item-and-respects-kind
  (let [plant-all (facts/evidence-checklist "JPN" :plant)
        animal-all (facts/evidence-checklist "JPN" :animal)]
    (is (facts/required-evidence-satisfied? "JPN" :plant plant-all))
    (is (not (facts/required-evidence-satisfied? "JPN" :plant (rest plant-all))))
    (is (not (facts/required-evidence-satisfied? "JPN" :plant animal-all))
        "the animal-health checklist does not satisfy the plant checklist -- the certificates are not interchangeable")
    (is (not (facts/required-evidence-satisfied? "ATL" :plant plant-all)) "no spec-basis -> never satisfied")))
