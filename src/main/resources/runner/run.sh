#!/bin/sh
T=/work/.timing
: > "$T"
echo "START $(date +%s%N)" >> "$T"
{ echo BEFORE; times; } >> "$T"
"$@"
CODE=$?
{ echo AFTER; times; } >> "$T"
echo "END $(date +%s%N)" >> "$T"
echo "CODE $CODE" >> "$T"
exit $CODE
