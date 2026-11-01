
    /** Lista campañas. Filtros opcionales: /api/campanas?vacuna=sarampion&distrito=miraflores */
    @GetMapping
    public List<Campana> listar(@RequestParam(defaultValue = "") String vacuna,
                                @RequestParam(defaultValue = "") String distrito) {
        return repo.findByVacunaContainingIgnoreCaseAndEstablecimientoDistritoContainingIgnoreCase(
                vacuna, distrito);
    }
