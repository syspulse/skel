## skel-cron

----

### Running with Cron expression

To avoid auto-gobbling and expansion it scrips, it must be quoted:

```
./run.sh --cron="*/1 * * * * *"
```