STORE=$1
OUTPUT=${2:-tokens.json}

>$OUTPUT

for f in $STORE/*; do
   echo $f
   cat $f >>$OUTPUT
   echo "" >>$OUTPUT
done

wc -l $OUTPUT