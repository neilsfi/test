/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package org.openrefine.operations.recon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openrefine.browsing.EngineConfig;
import org.openrefine.browsing.RowVisitor;
import org.openrefine.model.Cell;
import org.openrefine.model.ColumnMetadata;
import org.openrefine.model.Project;
import org.openrefine.model.Recon;
import org.openrefine.model.ReconCandidate;
import org.openrefine.model.Row;
import org.openrefine.model.Recon.Judgment;
import org.openrefine.model.changes.CellChange;
import org.openrefine.model.changes.Change;
import org.openrefine.model.changes.ReconChange;
import org.openrefine.operations.EngineDependentMassCellOperation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReconMatchBestCandidatesOperation extends EngineDependentMassCellOperation {
    @JsonCreator
    public ReconMatchBestCandidatesOperation(
            @JsonProperty("engineConfig")
            EngineConfig engineConfig,
            @JsonProperty("columnName")
            String columnName) {
        super(engineConfig, columnName, false);
    }
    
    @JsonProperty
    public String getColumnName() {
        return _columnName;
    }

    @Override
    protected String getDescription() {
        return "Match each cell to its best recon candidate in column " + _columnName;
    }

    @Override
    protected String createDescription(ColumnMetadata column,
            List<CellChange> cellChanges) {
        
        return "Match each of " + cellChanges.size() + 
            " cells to its best candidate in column " + column.getName();
    }

    @Override
    protected RowVisitor createRowVisitor(Project project, List<CellChange> cellChanges, long historyEntryID) throws Exception {
        ColumnMetadata column = project.columnModel.getColumnByName(_columnName);
        
        return new RowVisitor() {
            int                 cellIndex;
            List<CellChange>    cellChanges;
            Map<Long, Recon>    dupReconMap = new HashMap<Long, Recon>();
            long                historyEntryID;
            
            public RowVisitor init(int cellIndex, List<CellChange> cellChanges, long historyEntryID) {
                this.cellIndex = cellIndex;
                this.cellChanges = cellChanges;
                this.historyEntryID = historyEntryID;
                return this;
            }
            
            @Override
            public void start(Project project) {
                // nothing to do
            }

            @Override
            public void end(Project project) {
                // nothing to do
            }

            @Override
            public boolean visit(Project project, int rowIndex, Row row) {
                if (cellIndex < row.cells.size()) {
                    Cell cell = row.cells.get(cellIndex);
                    if (cell != null && cell.recon != null) {
                        ReconCandidate candidate = cell.recon.getBestCandidate();
                        if (candidate != null) {
                            Recon newRecon;
                            if (dupReconMap.containsKey(cell.recon.id)) {
                                newRecon = dupReconMap.get(cell.recon.id);
                                newRecon.judgmentBatchSize++;
                            } else {
                                newRecon = cell.recon.dup(historyEntryID);
                                newRecon.judgmentBatchSize = 1;
                                newRecon.match = candidate;
                                newRecon.matchRank = 0;
                                newRecon.judgment = Judgment.Matched;
                                newRecon.judgmentAction = "mass";
                                
                                dupReconMap.put(cell.recon.id, newRecon);
                            }
                            Cell newCell = new Cell(
                                cell.value,
                                newRecon
                            );
                            
                            CellChange cellChange = new CellChange(rowIndex, cellIndex, cell, newCell);
                            cellChanges.add(cellChange);
                        }
                    }
                }
                return false;
            }
        }.init(column.getCellIndex(), cellChanges, historyEntryID);
    }
    
    @Override
    protected Change createChange(Project project, ColumnMetadata column, List<CellChange> cellChanges) {
        return new ReconChange(
            cellChanges, 
            _columnName, 
            column.getReconConfig(),
            null
        );
    }
}