-- ============================================================================
-- Optional: race-free primary-key allocation for the managed CRUD tables.
--
-- The application allocates new PKs with MAX(pk)+1 and a retry-on-collision
-- fallback, which can briefly contend under concurrent inserts. If a sequence
-- named  <TABLE>_SEQ  exists in the connected schema, GenericCrudService uses
-- its NEXTVAL instead — atomic, no retry. This script creates those sequences,
-- each seeded to MAX(pk)+1 so it never collides with existing rows.
--
-- Safe to run once per environment. Re-running errors on "name already used"
-- (ORA-00955) — drop first if you need to reseed. The app picks up a new
-- sequence within ~5 minutes (metadata cache TTL) or on restart.
-- ============================================================================

DECLARE
  -- table name -> pk column
  TYPE t_map IS TABLE OF VARCHAR2(30) INDEX BY VARCHAR2(40);
  pk      t_map;
  tbl     VARCHAR2(40);
  nextval NUMBER;
BEGIN
  pk('BPM_PROCESS_STATUS')        := 'ID';
  pk('DS_HOME_BANNER_CONFIG')     := 'ID';
  pk('DA_DONATION_CATEGORIES')    := 'CAT_ID';
  pk('DA_DONATION_ORGANIZATIONS') := 'ORG_ID';
  pk('DA_DONATION_PROJECTS')      := 'PRJ_ID';

  tbl := pk.FIRST;
  WHILE tbl IS NOT NULL LOOP
    -- seed = current MAX(pk) + 1
    EXECUTE IMMEDIATE
      'SELECT NVL(MAX(' || pk(tbl) || '), 0) + 1 FROM ' || tbl INTO nextval;

    BEGIN
      EXECUTE IMMEDIATE
        'CREATE SEQUENCE ' || tbl || '_SEQ START WITH ' || nextval ||
        ' INCREMENT BY 1 NOCACHE NOCYCLE';
      DBMS_OUTPUT.PUT_LINE('Created ' || tbl || '_SEQ starting at ' || nextval);
    EXCEPTION
      WHEN OTHERS THEN
        IF SQLCODE = -955 THEN  -- ORA-00955: name is already used by an existing object
          DBMS_OUTPUT.PUT_LINE('Skipped ' || tbl || '_SEQ (already exists)');
        ELSE
          RAISE;
        END IF;
    END;

    tbl := pk.NEXT(tbl);
  END LOOP;
END;
/
