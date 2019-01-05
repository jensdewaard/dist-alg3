#!/usr/local/bin/bash
INPUT_DIR="./input"

function index () {
    if [[ $2 -gt $3 ]]
    then
        row=$2
        col=$3
    else
        row=$3
        col=$2
    fi
    let row=$1*${row}
    let tmp=${row}*${col}
    echo $tmp
}

for size in {5..100..5}
do
    for p in 25 50 100
    do
        for n in 1 2 3 4 5
        do
            declare -a EDGES
            # Create the edge matrix
            for j in $(seq 1 ${size})
            do
                for i in $(seq ${j} ${size})
                do
                    if [[ ${i} -ne ${j} ]]
                    then
                        number=$RANDOM
                        let "number %= 100"
                        idx=$(index ${size} ${i} ${j})
                        if [[ ${number} -le ${p} ]]
                        then
                            EDGES[$idx]=1
                        else
                            EDGES[$idx]=0
                        fi
                    fi
                done
            done
            # Then use the edge matrix to write the files
            FILE="$INPUT_DIR/${size}_${p}_${n}"
            printf "" > ${FILE}
            for j in $(seq 1 ${size})
            do
                printf "$j " >> ${FILE}
                for i in $(seq 1 ${size})
                do
                    if [[ ${i} -ne ${j} ]]
                    then
                        idx=$(index ${size} ${i} ${j})
                        if [[ EDGES[$idx] -eq 1 ]]
                        then
                            printf "$i " >> ${FILE}
                        fi
                    fi
                done
                printf "s\n" >> ${FILE}
            done
            echo "Finished $FILE"
        done
    done
done
