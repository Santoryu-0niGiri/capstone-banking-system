package com.capstone.transaction.model;

public record TransferResult(MutationResult sourceResult, MutationResult destResult) {
}
