(ns toritsugi.murakumo-test
  (:require [clojure.test :refer [deftest is]]
            [toritsugi.murakumo :as toritsugi]))

(def full-attestations
  (into {}
        (map (fn [gate] [gate (str "attested-" (name gate))]))
        (distinct (mapcat :required-gates (vals toritsugi/cell-specs)))))

(deftest maps-all-legacy-toritsugi-cells
  (is (= #{"toritsugi_draft"
           "toritsugi_eligibility_match"
           "toritsugi_guide"
           "toritsugi_intake"
           "toritsugi_procedure_registry"
           "toritsugi_status_track"
           "toritsugi_submit"}
         (set (map :legacy-cell (vals toritsugi/cell-specs))))))

(deftest r0-gates-block-effects
  (let [plan (toritsugi/cell-plan :submit
                                  {:member-did "did:example:member"
                                   :procedure-id "proc-001"})]
    (is (= :blocked (:status plan)))
    (is (= [:council-lv6-ratification
            :toritsugi-baseline-review
            :charter-rider-scan-baseline
            :consent-gated-baseline
            :own-procedure-only-baseline
            :non-fabrication-baseline
            :verified-procedure-only-baseline
            :pii-encrypted-envelope-baseline
            :murakumo-only-inference-baseline
            :kotoba-only-substrate-baseline
            :transparent-force-audit-baseline
            :lawful-channel-only-baseline
            :member-self-submit-default-baseline
            :member-authorization-baseline
            :no-platform-held-key-baseline
            :daikou-r3-gate-baseline]
           (:missing-gates plan)))
    (is (empty? (:effects plan)))))

(deftest procedure-registry-never-fabricates
  (let [plan (toritsugi/cell-plan :procedure-registry
                                  {:attestations full-attestations
                                   :procedure-id "proc-001"
                                   :legal-basis-cid "bafkreilaw"
                                   :provenance-cid "bafkreiprov"})
        effect (first (:effects plan))]
    (is (= :ready (:status plan)))
    (is (= "com.etzhayyim.toritsugi.procedure" (:collection effect)))
    (is (= false (get-in effect [:record :fabricated])))
    (is (= true (get-in effect [:record :verifiedProcedureOnly])))))

(deftest eligibility-match-is-soft-signal-only
  (let [attestations (dissoc full-attestations :no-eligibility-determination-baseline)
        plan (toritsugi/cell-plan :eligibility-match
                                  {:attestations attestations
                                   :match-id "match-001"})]
    (is (= :blocked (:status plan)))
    (is (= [:no-eligibility-determination-baseline] (:missing-gates plan)))))

(deftest guide-keeps-upl-boundary
  (let [attestations (-> full-attestations
                         (dissoc :upl-boundary-baseline)
                         (dissoc :licensed-counsel-route-baseline))
        plan (toritsugi/cell-plan :guide
                                  {:attestations attestations
                                   :guide-id "guide-001"})]
    (is (= :blocked (:status plan)))
    (is (= [:upl-boundary-baseline :licensed-counsel-route-baseline]
           (:missing-gates plan)))))

(deftest draft-is-input-assist-not-agency
  (let [plan (toritsugi/cell-plan :draft
                                  {:attestations full-attestations
                                   :draft-id "draft-001"
                                   :encrypted-payload-cid "bafkreidraft"})
        effect (first (:effects plan))]
    (is (= :ready (:status plan)))
    (is (= "com.etzhayyim.toritsugi.applicationDraft" (:collection effect)))
    (is (= false (get-in effect [:record :documentPreparationAgency])))
    (is (= false (get-in effect [:record :inlinePii])))))

(deftest submit-defaults-to-member-self-submit
  (let [plan (toritsugi/cell-plan :submit
                                  {:attestations full-attestations
                                   :submission-id "sub-001"
                                   :draft-id "draft-001"})
        effect (first (:effects plan))]
    (is (= :ready (:status plan)))
    (is (= "com.etzhayyim.toritsugi.submissionRecord" (:collection effect)))
    (is (= "member-self-submit" (get-in effect [:record :submissionMode])))
    (is (= true (get-in effect [:record :memberSelfSubmitDefault])))
    (is (= false (get-in effect [:record :agentOnBehalfEnabled])))
    (is (= false (get-in effect [:record :platformHeldKey])))))

(deftest status-track-routes-appeals-through-chigiri
  (let [attestations (dissoc full-attestations :chigiri-appeal-route-baseline)
        plan (toritsugi/cell-plan :status-track
                                  {:attestations attestations
                                   :status-id "status-001"})]
    (is (= :blocked (:status plan)))
    (is (= [:chigiri-appeal-route-baseline] (:missing-gates plan)))))

(deftest all-cell-plans-ready-when-attested
  (let [plans (toritsugi/all-cell-plans {:attestations full-attestations
                                         :member-did "did:example:member"
                                         :procedure-id "proc-001"
                                         :guide-id "guide-001"
                                         :match-id "match-001"
                                         :draft-id "draft-001"
                                         :submission-id "sub-001"
                                         :status-id "status-001"
                                         :legal-basis-cid "bafkreilaw"
                                         :provenance-cid "bafkreiprov"
                                         :consent-cid "bafkreiconsent"
                                         :encrypted-payload-cid "bafkreiencrypted"
                                         :computed-at "2026-06-29T00:00:00Z"})]
    (is (= (set (keys toritsugi/cell-specs)) (set (keys plans))))
    (is (every? #(= :ready (:status %)) (vals plans)))
    (is (= 7 (count (mapcat :effects (vals plans)))))))
