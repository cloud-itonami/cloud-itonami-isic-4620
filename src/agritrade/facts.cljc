(ns agritrade.facts
  "Per-jurisdiction, per-consignment-kind biosecurity regulatory catalog
  -- the G2-style spec-basis table the Agri Trading Governor checks
  every `:biosecurity/verify` proposal against ('did the advisor cite
  an OFFICIAL public source for this jurisdiction's phytosanitary /
  animal-health / quarantine requirements, or did it invent one?').

  Wholesale of agricultural raw materials and live animals (ISIC 4620)
  is UNUSUAL among this fleet's wholesale verticals in that a single
  ISIC code spans TWO genuinely different biosecurity regimes, governed
  by different statutes (and, in some jurisdictions, different
  agencies) depending on WHAT is being traded:

    - plant/grain/feed/seed/fibre consignments -- phytosanitary law
      (plant pest and disease control: quarantine pests, seed-borne
      pathogens, invasive weeds in a grain cargo).
    - live-animal/livestock consignments -- animal (veterinary) health
      law (transmissible animal disease control: foot-and-mouth
      disease, avian influenza, African swine fever).

  Unlike the fuel-wholesale sibling `cloud-itonami-isic-4671` (one
  commodity class, one excise/sanctions regime) or the general-trading
  sibling `cloud-itonami-isic-4690` (many UNRELATED commodity classes,
  one uniform export-control/sanctions regime regardless of which good
  is in the order), this vertical's regulatory catalog is keyed by BOTH
  jurisdiction AND `:consignment-kind` (`:plant` | `:animal`): the same
  country can, and typically does, run the plant-health regime and the
  animal-health regime under two different statutes (sometimes even two
  different agencies), each with its own certificate. See
  `agritrade.governor`'s `phytosanitary-certificate-missing-violations`
  / `animal-health-certificate-missing-violations` for why this is
  modeled as TWO distinct governor checks rather than one generic
  'certificate-missing' check.

  Each entry below is a REAL jurisdiction with a REAL biosecurity
  regime for both consignment kinds:

    - Japan (JPN): 植物防疫法 (Plant Protection Act) for plant
      consignments, and 家畜伝染病予防法 (Act on Domestic Animal
      Infectious Disease Control) for animal consignments -- both under
      the umbrella of 農林水産省 (MAFF, the Ministry of Agriculture,
      Forestry and Fisheries), but administered on the ground by two
      different field-office networks: 植物防疫所 (Plant Protection
      Stations) for plant, and 動物検疫所 / 家畜保健衛生所 (Animal
      Quarantine Service / Livestock Hygiene Service Centers) for
      animal.
    - United States (USA): the Plant Protection Act (7 U.S.C. §7701 et
      seq.) for plant consignments, and the Animal Health Protection Act
      (7 U.S.C. §8301 et seq.) for animal consignments -- both
      administered by the SAME federal agency, APHIS (Animal and Plant
      Health Inspection Service, USDA), but under two distinct
      statutes with two distinct certificate regimes (a phytosanitary
      certificate vs. a certificate of veterinary inspection / import
      permit).
    - United Kingdom (GBR): plant health legislation (Plant Health
      etc. (Amendment) (England) Regulations 2020, and the equivalent
      devolved-administration instruments for Scotland/Wales/Northern
      Ireland) for plant consignments, and animal health legislation
      (Animal Health Act 1981; Trade in Animals and Related Products
      Regulations 2011 / TARP) for animal consignments -- both, post-
      Brexit, administered by the SAME agency, APHA (the Animal and
      Plant Health Agency -- its very name reflects the same plant/
      animal regulatory split this catalog encodes).
    - Germany (DEU), representing the EU regime (directly applicable in
      every member state): Regulation (EU) 2016/2031 (the Plant Health
      Law) for plant consignments, and Regulation (EU) 2016/429 (the
      Animal Health Law) for animal consignments -- enforced in Germany
      through the Länder's Pflanzenschutzdienst (plant protection
      service) and Veterinärämter (veterinary offices) respectively,
      with federal scientific/coordinating roles held by the Julius
      Kühn-Institut (JKI, plant) and the Friedrich-Loeffler-Institut
      (FLI, animal) under the Bundesministerium für Ernährung und
      Landwirtschaft (BMEL).
    - Brazil (BRA) -- ANIMAL consignments only for now: Instrução
      Normativa GM/MAPA nº 9, de 16 de junho de 2021 (D.O.U.
      24/06/2021), issued by the Ministério da Agricultura, Pecuária e
      Abastecimento (MAPA) / Secretaria de Defesa Agropecuária (SDA),
      approves the printed model and establishes the electronic
      format (e-GTA) of the Guia de Trânsito Animal (GTA) -- the
      permit required nationwide for the transit of live animals,
      fertile eggs and other animal-multiplication material between
      establishments/jurisdictions inside Brazil. Fetched and read
      directly from MAPA's own official legislation listing for this
      subject (https://www.gov.br/agricultura/pt-br/assuntos/
      sanidade-animal-e-vegetal/saude-animal/transito-animal/cgtqa-legis)
      -- NOT training-time recall. BRA's PLANT/phytosanitary
      spec-basis has NOT been independently verified and is
      deliberately absent from the catalog below; see `coverage`,
      which will correctly keep reporting BRA as not-fully-covered
      until that companion entry is added with an equally verified
      primary source.

  The required-evidence set mirrors the counterparty-diligence evidence
  every fleet sibling's catalog carries (credit-clearance record,
  contract/PO, sanctions-screening record) PLUS the kind-specific
  biosecurity certificate: a phytosanitary certificate for plant
  consignments, an animal-health/veterinary certificate for animal
  consignments -- the real document a customs/quarantine inspector
  actually demands before a grain cargo or a livestock consignment
  crosses a jurisdictional or import boundary.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries. I do not have live
  web access; the statute/regulation names and owner-authority names
  above are cited from training-time knowledge with reasonable
  confidence for the headline instruments (植物防疫法 / 家畜伝染病予防法
  / MAFF; the Plant Protection Act / Animal Health Protection Act /
  APHIS; Regulation (EU) 2016/2031 / Regulation (EU) 2016/429; APHA),
  but the exact German Länder-level implementing-act names in
  particular should be independently verified before this catalog is
  relied on operationally -- see `docs/business-model.md`
  'Jurisdiction coverage (honest)'.")

(def catalog
  "iso3 -> {:plant {..} :animal {..}} -- TWO requirement maps per
  jurisdiction, one per `:consignment-kind`. `:required-evidence` is
  the counterparty-diligence + biosecurity evidence set (credit-
  clearance record, contract/PO, sanctions-screening record, PLUS the
  kind-specific certificate); `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2 citation the governor requires before any
  `:biosecurity/verify` proposal can commit."
  {"JPN"
   {:plant {:name "JPN" :kind :plant
            :owner-authority "農林水産省 (MAFF) 植物防疫所 (Plant Protection Station)"
            :legal-basis "植物防疫法 (Plant Protection Act)"
            :provenance "https://www.maff.go.jp/"
            :required-evidence ["credit-clearance record"
                                "contract/PO"
                                "sanctions-screening (OFAC/equivalent) record"
                                "phytosanitary certificate"]}
    :animal {:name "JPN" :kind :animal
             :owner-authority "農林水産省 (MAFF) 動物検疫所 / 家畜保健衛生所 (Animal Quarantine Service / Livestock Hygiene Service Center)"
             :legal-basis "家畜伝染病予防法 (Act on Domestic Animal Infectious Disease Control)"
             :provenance "https://www.maff.go.jp/"
             :required-evidence ["credit-clearance record"
                                 "contract/PO"
                                 "sanctions-screening (OFAC/equivalent) record"
                                 "animal health certificate (veterinary inspection)"]}}
   "USA"
   {:plant {:name "USA" :kind :plant
            :owner-authority "Animal and Plant Health Inspection Service (APHIS), USDA"
            :legal-basis "Plant Protection Act (7 U.S.C. §7701 et seq.)"
            :provenance "https://www.aphis.usda.gov/"
            :required-evidence ["credit-clearance record"
                                "contract/PO"
                                "sanctions-screening (OFAC/equivalent) record"
                                "phytosanitary certificate"]}
    :animal {:name "USA" :kind :animal
             :owner-authority "Animal and Plant Health Inspection Service (APHIS), USDA"
             :legal-basis "Animal Health Protection Act (7 U.S.C. §8301 et seq.)"
             :provenance "https://www.aphis.usda.gov/"
             :required-evidence ["credit-clearance record"
                                 "contract/PO"
                                 "sanctions-screening (OFAC/equivalent) record"
                                 "animal health certificate (certificate of veterinary inspection)"]}}
   "GBR"
   {:plant {:name "GBR" :kind :plant
            :owner-authority "Animal and Plant Health Agency (APHA)"
            :legal-basis "Plant Health etc. (Amendment) (England) Regulations 2020 (and the equivalent devolved-administration instruments)"
            :provenance "https://www.gov.uk/government/organisations/animal-and-plant-health-agency"
            :required-evidence ["credit-clearance record"
                                "contract/PO"
                                "sanctions-screening (OFAC/equivalent) record"
                                "phytosanitary certificate"]}
    :animal {:name "GBR" :kind :animal
             :owner-authority "Animal and Plant Health Agency (APHA)"
             :legal-basis "Animal Health Act 1981; Trade in Animals and Related Products Regulations 2011 (TARP)"
             :provenance "https://www.gov.uk/government/organisations/animal-and-plant-health-agency"
             :required-evidence ["credit-clearance record"
                                 "contract/PO"
                                 "sanctions-screening (OFAC/equivalent) record"
                                 "animal health certificate (veterinary inspection)"]}}
   "DEU"
   {:plant {:name "DEU" :kind :plant
            :owner-authority "Julius Kühn-Institut (JKI) / Länder Pflanzenschutzdienst, under Bundesministerium für Ernährung und Landwirtschaft (BMEL)"
            :legal-basis "Regulation (EU) 2016/2031 (Plant Health Law)"
            :provenance "https://www.julius-kuehn.de/"
            :required-evidence ["credit-clearance record"
                                "contract/PO"
                                "sanctions-screening (OFAC/equivalent) record"
                                "phytosanitary certificate"]}
    :animal {:name "DEU" :kind :animal
             :owner-authority "Friedrich-Loeffler-Institut (FLI) / Länder Veterinärämter, under Bundesministerium für Ernährung und Landwirtschaft (BMEL)"
             :legal-basis "Regulation (EU) 2016/429 (Animal Health Law)"
             :provenance "https://www.fli.de/"
             :required-evidence ["credit-clearance record"
                                 "contract/PO"
                                 "sanctions-screening (OFAC/equivalent) record"
                                 "animal health certificate (veterinary inspection)"]}}
   ;; NOTE: BRA has an :animal entry only -- :plant/phytosanitary has
   ;; NOT been independently verified yet and is deliberately omitted
   ;; (see the ns docstring and `coverage`). Do not add a :plant map
   ;; here without an equally verified primary source.
   "BRA"
   {:animal {:name "BRA" :kind :animal
             :owner-authority "Ministério da Agricultura, Pecuária e Abastecimento (MAPA) / Secretaria de Defesa Agropecuária (SDA)"
             :legal-basis "Instrução Normativa GM/MAPA nº 9, de 16 de junho de 2021 (D.O.U. 24/06/2021) -- Guia de Trânsito Animal (GTA/e-GTA)"
             :provenance "https://www.gov.br/agricultura/pt-br/assuntos/sanidade-animal-e-vegetal/saude-animal/transito-animal/cgtqa-legis/in-mapa-no-9-16-06-2021.pdf"
             :required-evidence ["credit-clearance record"
                                 "contract/PO"
                                 "sanctions-screening (OFAC/equivalent) record"
                                 "animal health certificate (Guia de Trânsito Animal -- GTA/e-GTA)"]}}})

(defn spec-basis
  "The [iso3 kind] requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to verify,
  dispatch or invoice on it. `kind` is `:plant` or `:animal`."
  [iso3 kind]
  (get-in catalog [iso3 kind]))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have BOTH a plant and an animal spec-basis entry. Never
  report a missing jurisdiction (or a jurisdiction missing either
  kind) as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [full? (fn [iso3] (and (get-in catalog [iso3 :plant])
                               (get-in catalog [iso3 :animal])))
         have (filter full? iso3s)
         missing (remove full? iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4620 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis for "
                 "BOTH consignment kinds (plant AND animal). This is a "
                 "starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `agritrade.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `[iso3 kind]`? Missing spec-basis ->
  never satisfied."
  [iso3 kind submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3 kind)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3 kind]
  (:required-evidence (spec-basis iso3 kind) []))
