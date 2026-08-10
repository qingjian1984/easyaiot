-- ADR-014 U004：消费者 Inbox 空表卸载入口；不得用于日常应用回滚。
BEGIN;

DO $$
BEGIN
    IF to_regclass('public.power_model_event_inbox') IS NOT NULL
       AND EXISTS (SELECT 1 FROM public.power_model_event_inbox LIMIT 1) THEN
        RAISE EXCEPTION 'U004 refused: power_model_event_inbox is not empty';
    END IF;
END
$$;

DROP TABLE IF EXISTS public.power_model_event_inbox;

COMMIT;
