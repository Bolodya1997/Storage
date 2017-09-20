package ru.nsu.fit.popov.storage.memory;

import ru.nsu.fit.popov.storage.net.Address;

import java.util.*;

class ReplicationPolicy {

    final static int REPLICATION_DEGREE = 3;

    private final Map<Address, Boolean> addresses = new HashMap<>();

    ReplicationPolicy(Collection<Address> addresses) {
        for (Address address : addresses) {
            this.addresses.put(address, true);
        }
    }

    void fail(Address address) {
        addresses.put(address, false);
    }

    boolean canSave(Address myAddress, String key) {
        final int myNumber = computeMyNumber(myAddress);
        final List<Integer> numbers = fillNumbers();

        int parts = addresses.size();
        double width = Integer.MAX_VALUE;
        double current = key.hashCode();
        int count = 0;
        int pos, number;

        while (count < REPLICATION_DEGREE) {
            width /= parts; //  assumes that parts is always > 0

            pos = (int) Math.round(current / width);
            number = numbers.get(pos);
            if (number == myNumber) {
                return true;
            } else {
                if (number >= 0)
                    ++count;
                numbers.remove(pos);
            }

            current -= pos * width;
            --parts;
        }

        return false;
    }

    private int computeMyNumber(Address myAddress) {
        int myNumber = 0;
        for (Address address : addresses.keySet()) {
            if (Objects.equals(address, myAddress))
                return myNumber;
            ++myNumber;
        }

        throw new RuntimeException("Address is out of list");
    }

    private List<Integer> fillNumbers() {
        List<Integer> numbers = new ArrayList<>();
        for (Map.Entry<Address, Boolean> entry : addresses.entrySet()) {
            if (entry.getValue())
                numbers.add(numbers.size());
            else
                numbers.add(-1);
        }
        return numbers;
    }
}
