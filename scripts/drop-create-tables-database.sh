docker cp ./src/sql/schema-drop-tables.sql postgres:/tmp/
docker cp ./src/sql/schema-create-tables.sql postgres:/tmp/

docker exec postgres psql -f ./tmp/schema-drop-tables.sql -U magadiflo -d db_spring_batch
docker exec postgres psql -f ./tmp/schema-create-tables.sql -U magadiflo -d db_spring_batch