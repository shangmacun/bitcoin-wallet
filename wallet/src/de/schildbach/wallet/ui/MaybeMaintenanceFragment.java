/*
 * Copyright 2014-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import java.util.List;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.DeterministicUpgradeRequiresPassword;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.ListenableFuture;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.BlockchainStateLiveData;
import de.schildbach.wallet.data.WalletLiveData;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.ui.send.MaintenanceDialogFragment;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

/**
 * @author Andreas Schildbach
 */
public class MaybeMaintenanceFragment extends Fragment {
    private static final String FRAGMENT_TAG = MaybeMaintenanceFragment.class.getName();

    public static void add(final FragmentManager fm) {
        Fragment fragment = fm.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new MaybeMaintenanceFragment();
            fm.beginTransaction().add(fragment, FRAGMENT_TAG).commit();
        }
    }

    private ViewModel viewModel;

    public static class ViewModel extends AndroidViewModel {
        private final WalletApplication application;
        private WalletLiveData wallet;
        private BlockchainStateLiveData blockchainState;
        private boolean dialogWasShown = false;

        public ViewModel(final Application application) {
            super(application);
            this.application = (WalletApplication) application;
        }

        public WalletLiveData getWallet() {
            if (wallet == null)
                wallet = new WalletLiveData(application);
            return wallet;
        }

        public BlockchainStateLiveData getBlockchainState() {
            if (blockchainState == null)
                blockchainState = new BlockchainStateLiveData(application);
            return blockchainState;
        }

        public void setDialogWasShown() {
            dialogWasShown = true;
        }

        public boolean getDialogWasShown() {
            return dialogWasShown;
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = ViewModelProviders.of(this).get(ViewModel.class);
        viewModel.getWallet().observe(this, new Observer<Wallet>() {
            @Override
            public void onChanged(final Wallet wallet) {
                maybeShowDialog();
            }
        });
        viewModel.getBlockchainState().observe(this, new Observer<BlockchainState>() {
            @Override
            public void onChanged(final BlockchainState blockchainState) {
                maybeShowDialog();
            }
        });
    }

    private void maybeShowDialog() {
        if (viewModel.getDialogWasShown())
            return;
        final Wallet wallet = viewModel.getWallet().getValue();
        final BlockchainState blockchainState = viewModel.getBlockchainState().getValue();
        if (blockchainState != null && !blockchainState.replaying && wallet != null && maintenanceRecommended(wallet)) {
            MaintenanceDialogFragment.show(getFragmentManager());
            viewModel.setDialogWasShown();
        }
    }

    private boolean maintenanceRecommended(final Wallet wallet) {
        try {
            final ListenableFuture<List<Transaction>> result = wallet.doMaintenance(null, false);
            return !result.get().isEmpty();
        } catch (final DeterministicUpgradeRequiresPassword x) {
            return true;
        } catch (final Exception x) {
            throw new RuntimeException(x);
        }
    }
}
