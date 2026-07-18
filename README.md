# toritsugi - Government Procedure Concierge Actor

**DID**: `did:web:toritsugi.etzhayyim.com`
**Namespace**: `com.etzhayyim.toritsugi.*`
**Status**: migration boundary for citizen-facing government procedure cells

## Migration Boundary

`src/toritsugi/murakumo.cljc` is the Murakumo-facing cljc actor boundary for the
legacy toritsugi kotoba-kotodama cells:

- `toritsugi_procedure_registry` -> `procedure`
- `toritsugi_intake` -> `procedureGuide`
- `toritsugi_eligibility_match` -> `benefitMatch`
- `toritsugi_guide` -> `procedureGuide`
- `toritsugi_draft` -> `applicationDraft`
- `toritsugi_submit` -> `submissionRecord`
- `toritsugi_status_track` -> `statusTrack`

The actor is guidance and input assistance only. It does not fabricate
procedures, make eligibility/legal determinations, act as an official municipal
channel, hold member signing keys, or perform `agent-on-behalf` submission unless
the R3 lawful代理 gate is explicitly attested.
