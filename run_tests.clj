(require '[clojure.test :as t])

(doseq [ns-sym '[toritsugi.flow-test
                  toritsugi.governor-contract-test
                  toritsugi.methods.test-charter-gates
                  toritsugi.methods.test-manifest-invariants
                  toritsugi.social-test
                  toritsugi.registry-contract-test
                  toritsugi.repository-contract-test]]
  (require ns-sym))

(let [result (apply t/run-tests
                    '[toritsugi.flow-test
                      toritsugi.governor-contract-test
                      toritsugi.methods.test-charter-gates
                      toritsugi.methods.test-manifest-invariants
                      toritsugi.social-test
                      toritsugi.registry-contract-test
                      toritsugi.repository-contract-test])]
  (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1)))
