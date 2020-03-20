package com.github.brewin.mvicoroutines.presentation.main

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.brewin.mvicoroutines.R
import com.github.brewin.mvicoroutines.data.remote.GitHubDataSource
import com.github.brewin.mvicoroutines.data.repository.GitHubRepositoryImpl
import com.github.brewin.mvicoroutines.domain.entity.RepoEntity
import com.github.brewin.mvicoroutines.presentation.common.GenericListAdapter
import com.github.brewin.mvicoroutines.presentation.common.hideKeyboard
import com.github.brewin.mvicoroutines.presentation.common.provideMachine
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.main_fragment.*
import kotlinx.android.synthetic.main.repo_item.view.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.appcompat.QueryTextEvent
import reactivecircus.flowbinding.appcompat.queryTextEvents
import reactivecircus.flowbinding.material.dismissEvents
import reactivecircus.flowbinding.swiperefreshlayout.refreshes

class MainFragment : Fragment() {

    private lateinit var machine: MainMachine

    private val repoListAdapter by lazy {
        GenericListAdapter<ConstraintLayout, RepoEntity>(R.layout.repo_item) { layout, repoItem ->
            layout.repoName.text = repoItem.name
        }
    }

    private val errorSnackbar by lazy {
        Snackbar.make(requireView(), "", Snackbar.LENGTH_LONG)
            .apply {
                dismissEvents()
                    .onEach { machine.events.send(MainEvent.ErrorMessageDismiss) }
                    .launchIn(lifecycleScope)
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        machine = provideMachine {
            val initial = savedInstanceState?.getParcelable(SAVED_STATE_KEY) ?: MainState()
            val gitHubRepository = GitHubRepositoryImpl(GitHubDataSource())
            MainMachine(initial, gitHubRepository)
        }
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        repoListView.adapter = repoListAdapter

        swipeRefreshLayout
            .refreshes()
            .onEach { machine.events.send(MainEvent.RefreshSwipe) }
            .launchIn(lifecycleScope)

        machine.states
            .onEach { it.render() }
            .launchIn(lifecycleScope)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)

        menu.findItem(R.id.action_search).actionView
            .let { it as SearchView }
            .queryTextEvents()
            .filterIsInstance<QueryTextEvent.QuerySubmitted>()
            .onEach {
                machine.events.send(MainEvent.QuerySubmit(it.queryText.toString()))
                hideKeyboard()
            }.launchIn(lifecycleScope)

        menu.findItem(R.id.action_refresh)
            .clicks()
            .onEach { machine.events.send(MainEvent.RefreshClick) }
            .launchIn(lifecycleScope)

        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(SAVED_STATE_KEY, machine.state)
        super.onSaveInstanceState(outState)
    }

    private fun MainState.render() {
        if (shouldShowError && !errorSnackbar.isShownOrQueued) {
            errorSnackbar.setText(errorMessage).show()
        }
        swipeRefreshLayout.isRefreshing = isInProgress
        repoListAdapter.items = searchResults
    }

    companion object {
        const val SAVED_STATE_KEY = "main_fragment_saved_state"
    }
}