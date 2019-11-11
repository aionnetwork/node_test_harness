package org.aion.harness.statistics;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aion.harness.main.types.Block;

public final class BlockStatistics {
    private final int numberOfTransactions;
    private final List<Block> blocks;

    private boolean statisticsHaveBeenComputed = false;
    private int numberOfUniqueBlocks = 0;

    private BigDecimal maximumBlockEnergyLimit = null;
    private BigDecimal minimumBlockEnergyLimit = null;
    private BigDecimal meanBlockEnergyLimit = null;
    private BigDecimal blockEnergyLimitsStandardDeviation = null;

    private BigDecimal maximumBlockEnergyUsed = null;
    private BigDecimal minimumBlockEnergyUsed = null;
    private BigDecimal meanBlockEnergyUsed = null;
    private BigDecimal blockEnergyUsedStandardDeviation = null;

    private BigDecimal maximumBlockEnergyUsedPercentage = null;
    private BigDecimal minimumBlockEnergyUsedPercentage = null;
    private BigDecimal meanEnergyUsedPercentage = null;
    private BigDecimal blockEnergyUsedPercentageStandardDeviation = null;

    private BlockStatistics(int numberOfTransactions, List<Block> blocks) {
        if (blocks == null) {
            throw new NullPointerException("Cannot construct BlockStatistics with null list of blocks.");
        }
        if (numberOfTransactions != blocks.size()) {
            throw new IllegalArgumentException("Cannot construct BlockStatistics when numberOfTransactions differs from blocks.size()");
        }

        this.numberOfTransactions = numberOfTransactions;
        this.blocks = new ArrayList<>(blocks);
    }

    public static BlockStatistics from(int numberOfTransactions, List<Block> blocks) {
        return new BlockStatistics(numberOfTransactions, blocks);
    }

    /**
     * Prints to console some basic block statistics.
     *
     * These statistics are mostly concerned with block energy usage.
     *
     * @param decimalPrecision The number of decimal places for these numbers to be accurate to.
     */
    public void printStatistics(int decimalPrecision) {
        if (!this.statisticsHaveBeenComputed) {
            computeStatistics(decimalPrecision);
        }

        System.out.println("---------------------------------------------------------------------");
        System.out.println(this.numberOfTransactions + " transaction(s) were sealed into " + this.numberOfUniqueBlocks + " block(s).");
        System.out.println();
        System.out.println("Maximum block energy limit = " + this.maximumBlockEnergyLimit.toPlainString());
        System.out.println("Minimum block energy limit = " + this.minimumBlockEnergyLimit.toPlainString());
        System.out.println("Mean block energy limit = " + this.meanBlockEnergyLimit.toPlainString());
        System.out.println("Block energy limit standard deviation = " + this.blockEnergyLimitsStandardDeviation.toPlainString());
        System.out.println();
        System.out.println("Maximum block energy used = " + this.maximumBlockEnergyUsed.toPlainString());
        System.out.println("Minimum block energy used = " + this.minimumBlockEnergyUsed.toPlainString());
        System.out.println("Mean block energy used = " + this.meanBlockEnergyUsed.toPlainString());
        System.out.println("Block energy used standard deviation = " + this.blockEnergyUsedStandardDeviation.toPlainString());
        System.out.println();
        System.out.println("Maximum percentage of block energy used = " + this.maximumBlockEnergyUsedPercentage.toPlainString() + "%");
        System.out.println("Minimum percentage of block energy used = " + this.minimumBlockEnergyUsedPercentage.toPlainString() + "%");
        System.out.println("Mean percentage of block energy used = " + this.meanEnergyUsedPercentage.toPlainString() + "%");
        System.out.println("Percentage of block energy used standard deviation = " + this.blockEnergyUsedPercentageStandardDeviation.toPlainString() + "%");
        System.out.println("---------------------------------------------------------------------");
    }

    private void computeStatistics(int precision) {
        Set<BigInteger> blockNumbers = new HashSet<>();

        BigDecimal totalEnergyLimit = BigDecimal.ZERO;
        BigDecimal totalEnergyUsed = BigDecimal.ZERO;
        BigDecimal totalEnergyUsedPercentage = BigDecimal.ZERO;
        List<BigDecimal> energyLimits = new ArrayList<>();
        List<BigDecimal> energyUsedAmounts = new ArrayList<>();
        List<BigDecimal> energyUsedPercentages = new ArrayList<>();

        for (Block block : this.blocks) {
            blockNumbers.add(block.number);

            BigDecimal energyLimit = BigDecimal.valueOf(block.blockEnergyLimit);
            energyLimits.add(energyLimit);

            BigDecimal energyUsed = BigDecimal.valueOf(block.blockEnergyUsed);
            energyUsedAmounts.add(energyUsed);

            BigDecimal usedPercentage = energyUsed.divide(energyLimit, precision, RoundingMode.HALF_UP);
            energyUsedPercentages.add(usedPercentage);

            this.maximumBlockEnergyLimit = (this.maximumBlockEnergyLimit == null)
                ? energyLimit
                : ((this.maximumBlockEnergyLimit.compareTo(energyLimit) < 0) ? energyLimit : this.maximumBlockEnergyLimit);

            this.minimumBlockEnergyLimit = (this.minimumBlockEnergyLimit == null)
                ? energyLimit
                : ((this.minimumBlockEnergyLimit.compareTo(energyLimit) > 0) ? energyLimit : this.minimumBlockEnergyLimit);

            this.maximumBlockEnergyUsed = (this.maximumBlockEnergyUsed == null)
                ? energyUsed
                : ((this.maximumBlockEnergyUsed.compareTo(energyUsed) < 0) ? energyUsed : this.maximumBlockEnergyUsed);

            this.minimumBlockEnergyUsed = (this.minimumBlockEnergyUsed == null)
                ? energyUsed
                : ((this.minimumBlockEnergyUsed.compareTo(energyUsed) > 0) ? energyUsed : this.minimumBlockEnergyUsed);

            this.maximumBlockEnergyUsedPercentage = (this.maximumBlockEnergyUsedPercentage == null)
                ? usedPercentage
                : ((this.maximumBlockEnergyUsedPercentage.compareTo(usedPercentage) < 0) ? usedPercentage : this.maximumBlockEnergyUsedPercentage);

            this.minimumBlockEnergyUsedPercentage = (this.minimumBlockEnergyUsedPercentage == null)
                ? usedPercentage
                : ((this.minimumBlockEnergyUsedPercentage.compareTo(usedPercentage) > 0) ? usedPercentage : this.minimumBlockEnergyUsedPercentage);

            totalEnergyLimit = totalEnergyLimit.add(energyLimit);
            totalEnergyUsed = totalEnergyUsed.add(energyUsed);
            totalEnergyUsedPercentage = totalEnergyUsedPercentage.add(usedPercentage);
        }

        this.numberOfUniqueBlocks = blockNumbers.size();

        BigDecimal numTransactions = BigDecimal.valueOf(this.numberOfTransactions);
        BigDecimal hundred = BigDecimal.valueOf(100);

        this.meanBlockEnergyLimit = totalEnergyLimit.divide(numTransactions, precision, RoundingMode.HALF_UP);
        this.blockEnergyLimitsStandardDeviation = computeStandardDeviation(energyLimits, this.meanBlockEnergyLimit, precision);
        this.meanBlockEnergyUsed = totalEnergyUsed.divide(numTransactions, precision, RoundingMode.HALF_UP);
        this.blockEnergyUsedStandardDeviation = computeStandardDeviation(energyUsedAmounts, this.meanBlockEnergyUsed, precision);
        this.meanEnergyUsedPercentage = totalEnergyUsedPercentage.divide(numTransactions, precision, RoundingMode.HALF_UP);
        this.blockEnergyUsedPercentageStandardDeviation = computeStandardDeviation(energyUsedPercentages, this.meanEnergyUsedPercentage, precision);

        this.maximumBlockEnergyUsedPercentage = this.maximumBlockEnergyUsedPercentage.multiply(hundred).setScale(2, RoundingMode.HALF_UP);
        this.minimumBlockEnergyUsedPercentage = this.minimumBlockEnergyUsedPercentage.multiply(hundred).setScale(2, RoundingMode.HALF_UP);
        this.meanEnergyUsedPercentage = this.meanEnergyUsedPercentage.multiply(hundred).setScale(2, RoundingMode.HALF_UP);
        this.blockEnergyUsedPercentageStandardDeviation = this.blockEnergyUsedPercentageStandardDeviation.multiply(hundred).setScale(2, RoundingMode.HALF_UP);

        this.statisticsHaveBeenComputed = true;
    }

    private BigDecimal computeStandardDeviation(List<BigDecimal> numbers, BigDecimal mean, int precision) {
        BigDecimal squares = BigDecimal.ZERO;

        for (BigDecimal number : numbers) {
            squares = squares.add(number.multiply(number));
        }

        BigDecimal variance = squares.divide(mean, precision, RoundingMode.HALF_UP);
        return variance.sqrt(new MathContext(precision, RoundingMode.HALF_UP));
    }

}
